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
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpotifyApiClient {

    private static final String BASE_URL = "https://api.spotify.com/v1";
    // Spotify limita a 100 URIs por llamada en los endpoints de agregar/reemplazar tracks.
    // Si se envían más, responde 400 Bad Request. Hay que dividir en batches.
    private static final int TRACK_BATCH_SIZE = 100;

    private final OAuth2AuthorizedClientService authorizedClientService;

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
        return restClient.post()
                .uri("/me/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(SpotifyDto.CreatePlaylistResponse.class);
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
            restClient.post()
                    .uri("/playlists/{playlistId}/items", playlistId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
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
        restClient.put()
                .uri("/playlists/{id}/items", playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(putBody)
                .retrieve()
                .toBodilessEntity();

        for (int i = TRACK_BATCH_SIZE; i < uris.size(); i += TRACK_BATCH_SIZE) {
            List<String> batch = uris.subList(i, Math.min(i + TRACK_BATCH_SIZE, uris.size()));
            String postBody = "{\"uris\":" + toJsonArray(batch) + "}";
            restClient.post()
                    .uri("/playlists/{id}/items", playlistId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(postBody)
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    public SpotifyDto.PlaylistItem fetchPlaylist(RestClient restClient, String playlistId) {
        return restClient.get()
                .uri("/playlists/{id}", playlistId)
                .retrieve()
                .body(SpotifyDto.PlaylistItem.class);
    }

    public void renamePlaylist(RestClient restClient, String playlistId, String newName) {
        String safeName = newName.replace("\"", "'");
        String body = "{\"name\":\"" + safeName + "\"}";
        restClient.put()
                .uri("/playlists/{id}", playlistId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    // Spotify requiere subir imagen en JPEG codificado en base64 (sin el prefijo "data:image/...").
    // Content-Type debe ser "image/jpeg" (no JSON). Tamaño máximo 256KB.
    public void uploadPlaylistImage(RestClient restClient, String playlistId, String base64Jpeg) {
        restClient.put()
                .uri("/playlists/{id}/images", playlistId)
                .contentType(MediaType.parseMediaType("image/jpeg"))
                .body(base64Jpeg)
                .retrieve()
                .toBodilessEntity();
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
