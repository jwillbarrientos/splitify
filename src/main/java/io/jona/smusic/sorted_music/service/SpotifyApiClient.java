package io.jona.smusic.sorted_music.service;

import io.jona.smusic.sorted_music.dto.SpotifyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpotifyApiClient {

    private static final String BASE_URL = "https://api.spotify.com/v1";
    // Spotify limita a 100 URIs por llamada en los endpoints de agregar/reemplazar tracks.
    // Si se envían más, responde 400 Bad Request. Hay que dividir en batches.
    private static final int TRACK_BATCH_SIZE = 100;
    // Intentos totales ante 429 Too Many Requests (1 original + 2 retries). El espera larga
    // entre reintentos le da tiempo a Spotify para drenar su ventana deslizante de rate
    // limit, permitiendo que playlists posteriores del lote se creen exitosamente mientras
    // la actual reintenta. Una primera versión con 1 retry + espera corta (10s) optimizaba
    // para fallar rápido, pero saturaba la ventana de Spotify y hacía que TODAS las
    // playlists del lote fallaran en cadena.
    private static final int MAX_RETRIES_429 = 3;
    // Cap en la espera — Spotify a veces pide 60s+ en Retry-After pero no queremos quedarnos
    // eternamente bloqueados. 30s es un balance entre respetar el rate limit y mantener
    // tiempos de respuesta tolerables.
    private static final long MAX_RETRY_SECONDS = 30;
    // Fallback cuando Spotify no manda el header Retry-After (raro pero posible).
    private static final long DEFAULT_RETRY_SECONDS = 30;

    private final OAuth2AuthorizedClientService authorizedClientService;

    // Envuelve una llamada HTTP para reintentar ante 429 Too Many Requests. Lee el header
    // Retry-After (en segundos, capado en MAX_RETRY_SECONDS) y duerme esa cantidad antes
    // de reintentar. Respeta el interrupt del thread si alguien cancela la operación.
    private <T> T withRetry429(Supplier<T> call) {
        int attempt = 1;
        while (true) {
            try {
                return call.get();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt >= MAX_RETRIES_429) {
                    long requested = rawRetryAfter(e);
                    log.warn("Spotify 429 persistente (Retry-After={}s, llevamos {} intento(s)). " +
                             "Propagando para que el caller siga con las otras operaciones.",
                            requested, attempt);
                    throw e;
                }
                long waitSeconds = parseRetryAfter(e);
                log.warn("Spotify 429 Too Many Requests — intento {}/{}, esperando {}s antes de reintentar",
                        attempt, MAX_RETRIES_429, waitSeconds);
                try {
                    Thread.sleep(waitSeconds * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                attempt++;
            }
        }
    }

    // Lee el Retry-After real que devolvió Spotify (para logueo). -1 si no viene el header.
    private long rawRetryAfter(HttpClientErrorException e) {
        String retryAfter = e.getResponseHeaders() != null
                ? e.getResponseHeaders().getFirst("Retry-After")
                : null;
        if (retryAfter == null || retryAfter.isBlank()) return -1;
        try {
            return Long.parseLong(retryAfter.trim());
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    // Lee el Retry-After y lo aplica al cap MAX_RETRY_SECONDS — nunca dormimos más de
    // eso aunque Spotify pida más.
    private long parseRetryAfter(HttpClientErrorException e) {
        long raw = rawRetryAfter(e);
        if (raw < 0) return DEFAULT_RETRY_SECONDS;
        long seconds = Math.max(1, raw);
        return Math.min(seconds, MAX_RETRY_SECONDS);
    }

    public RestClient createRestClient(OAuth2AuthenticationToken authentication) {
        String accessToken = getAccessToken(authentication);
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
    }

    public SpotifyDto.UserProfileResponse getUserProfile(RestClient restClient) {
        return restClient.get()
                .uri("/me")
                .retrieve()
                .body(SpotifyDto.UserProfileResponse.class);
    }

    public List<SpotifyDto.PlaylistItem> fetchAllUserPlaylists(RestClient restClient) {
        List<SpotifyDto.PlaylistItem> allItems = new ArrayList<>();
        String url = "/me/playlists?limit=50";

        while (url != null) {
            SpotifyDto.PlaylistsResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(SpotifyDto.PlaylistsResponse.class);
            if (response == null || response.items() == null) break;
            allItems.addAll(response.items());
            url = response.next();
        }

        return allItems;
    }

    // Trae todas las canciones "Liked Songs" paginando con limit=50 y siguiendo el campo `next`.
    // El total de la respuesta inicial se preserva para mantener `Playlist.totalTracks` correcto.
    public LikedSongsResult fetchAllLikedSongs(RestClient restClient) {
        List<SpotifyDto.TrackWrapper> allTracks = new ArrayList<>();
        String url = "/me/tracks?limit=50";
        int total = 0;
        boolean firstPage = true;

        while (url != null) {
            SpotifyDto.LikedTracksResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(SpotifyDto.LikedTracksResponse.class);
            if (response == null || response.items() == null) break;
            if (firstPage) {
                total = response.total();
                firstPage = false;
            }
            allTracks.addAll(response.items());
            url = response.next();
        }

        return new LikedSongsResult(allTracks, total);
    }

    // Envuelve los items + el `total` global que declara Spotify. Se guarda total aparte porque
    // `items.size()` puede ser menor (ej: canciones no disponibles en la región del usuario).
    public record LikedSongsResult(List<SpotifyDto.TrackWrapper> items, int total) {}

    public List<SpotifyDto.TrackWrapper> fetchAllPlaylistTracks(RestClient restClient, String playlistId) {
        List<SpotifyDto.TrackWrapper> allTracks = new ArrayList<>();
        String url = "/playlists/" + playlistId + "/items?limit=50";

        while (url != null) {
            SpotifyDto.TracksResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(SpotifyDto.TracksResponse.class);
            if (response == null || response.items() == null) break;
            allTracks.addAll(response.items());
            url = response.next();
        }

        return allTracks;
    }

    public SpotifyDto.CreatePlaylistResponse createPlaylist(RestClient restClient, String name) {
        // Reemplazar comillas dobles por simples para no romper el JSON que construimos a mano.
        // (No es escape "real"; si el usuario pone " en el nombre, se verá como ' en Spotify.)
        String safeName = name.replace("\"", "'");
        String body = "{\"name\":\"" + safeName + "\",\"public\":false,\"description\":\"Playlist creada por Splitify\"}";
        return withRetry429(() -> restClient.post()
                .uri("/me/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(SpotifyDto.CreatePlaylistResponse.class));
    }

    // Agrega tracks al final de una playlist. Divide en batches de TRACK_BATCH_SIZE porque
    // Spotify rechaza requests con más de 100 URIs.
    public void addTracks(RestClient restClient, String playlistId, List<String> trackIds) {
        List<String> uris = trackIds.stream()
                .map(id -> "spotify:track:" + id)
                .toList();

        for (int i = 0; i < uris.size(); i += TRACK_BATCH_SIZE) {
            List<String> batch = uris.subList(i, Math.min(i + TRACK_BATCH_SIZE, uris.size()));
            String body = "{\"uris\":" + toJsonArray(batch) + "}";
            withRetry429(() -> restClient.post()
                    .uri("/playlists/{playlistId}/items", playlistId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity());
        }
    }

    // Reemplaza TODAS las canciones de una playlist por `trackIds`. Patrón PUT + POST:
    //   1. PUT /playlists/{id}/items → reemplaza el contenido (Spotify limita a 100 URIs).
    //   2. Si hay más de 100, los extras se AGREGAN con POST en batches, porque PUT no acepta más.
    // Si omitiéramos el PUT inicial, las canciones viejas seguirían ahí además de las nuevas.
    public void replaceTracks(RestClient restClient, String playlistId, List<String> trackIds) {
        List<String> uris = trackIds.stream()
                .map(id -> "spotify:track:" + id)
                .toList();

        List<String> firstBatch = uris.subList(0, Math.min(TRACK_BATCH_SIZE, uris.size()));
        String putBody = "{\"uris\":" + toJsonArray(firstBatch) + "}";
        withRetry429(() -> restClient.put()
                .uri("/playlists/{id}/items", playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(putBody)
                .retrieve()
                .toBodilessEntity());

        for (int i = TRACK_BATCH_SIZE; i < uris.size(); i += TRACK_BATCH_SIZE) {
            List<String> batch = uris.subList(i, Math.min(i + TRACK_BATCH_SIZE, uris.size()));
            String postBody = "{\"uris\":" + toJsonArray(batch) + "}";
            withRetry429(() -> restClient.post()
                    .uri("/playlists/{id}/items", playlistId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(postBody)
                    .retrieve()
                    .toBodilessEntity());
        }
    }

    public SpotifyDto.PlaylistItem fetchPlaylist(RestClient restClient, String playlistId) {
        return withRetry429(() -> restClient.get()
                .uri("/playlists/{id}", playlistId)
                .retrieve()
                .body(SpotifyDto.PlaylistItem.class));
    }

    public void renamePlaylist(RestClient restClient, String playlistId, String newName) {
        String safeName = newName.replace("\"", "'");
        String body = "{\"name\":\"" + safeName + "\"}";
        withRetry429(() -> restClient.put()
                .uri("/playlists/{id}", playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    // Spotify requiere subir imagen en JPEG codificado en base64 (sin el prefijo "data:image/...").
    // Content-Type debe ser "image/jpeg" (no JSON). Tamaño máximo 256KB.
    public void uploadPlaylistImage(RestClient restClient, String playlistId, String base64Jpeg) {
        withRetry429(() -> restClient.put()
                .uri("/playlists/{id}/images", playlistId)
                .contentType(MediaType.parseMediaType("image/jpeg"))
                .body(base64Jpeg)
                .retrieve()
                .toBodilessEntity());
    }

    // Overload que usa RestClient en vez de OAuth token. Se usa durante la creación de
    // playlists para limpiar una playlist parcial si addTracks falló — reutilizamos el
    // RestClient que ya teníamos en vez de pedir la autenticación de nuevo.
    // Falla en silencio (log warn) porque el caller ya tiene una excepción que propagar
    // y no queremos oscurecerla con un error de limpieza.
    public void unfollowPlaylist(RestClient restClient, String playlistId) {
        try {
            restClient.delete()
                    .uri("/playlists/{id}/followers", playlistId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Playlist parcial '{}' limpiada de Spotify", playlistId);
        } catch (Exception e) {
            log.warn("No se pudo limpiar playlist parcial '{}' de Spotify: {}",
                    playlistId, e.getMessage());
        }
    }

    public void unfollowPlaylist(OAuth2AuthenticationToken authentication, String playlistId) {
        String accessToken = getAccessToken(authentication);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/playlists/" + playlistId + "/followers"))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Unfollow playlist '{}' response: {} {}", playlistId, response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("Could not unfollow playlist '{}' on Spotify: {}", playlistId, e.getMessage());
        }
    }

    private String getAccessToken(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión de Spotify expirada");
        }
        return client.getAccessToken().getTokenValue();
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
