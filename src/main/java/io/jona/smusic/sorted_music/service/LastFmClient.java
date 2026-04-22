package io.jona.smusic.sorted_music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Cliente de Last.fm (https://www.last.fm/api) para obtener tags de género por artista.
 *
 * Usamos este servicio en lugar del endpoint /v1/artists de Spotify, que fue removido en
 * febrero 2026 (y el campo `genres` fue eliminado del objeto Artist). Last.fm mantiene una
 * base de tags curada por la comunidad, con cobertura similar y sin costo (solo requiere
 * registrar una API key en last.fm/api/account/create).
 *
 * Endpoint usado: artist.gettoptags (un artista por request, no soporta batch).
 */
@Slf4j
@Service
public class LastFmClient {

    private static final String BASE_URL = "https://ws.audioscrobbler.com/2.0/";
    private static final String USER_AGENT = "Splitify (https://splitifyapp.app)";
    // Tomamos los primeros N tags (Last.fm los ordena por peso descendente).
    // Más que 5 agrega ruido al prompt de ChatGPT sin aportar señal.
    private static final int MAX_TAGS_PER_ARTIST = 5;

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;

    public LastFmClient(@Value("${lastfm.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Obtiene los tags de género de un artista desde Last.fm (endpoint `artist.gettoptags`).
     * Retorna lista vacía si la API key no está, Last.fm no tiene registro, o la llamada falla.
     * Usado como fallback cuando los tags específicos de la canción no están disponibles.
     */
    public List<String> fetchTopTags(String artistName) {
        if (!isConfigured() || artistName == null || artistName.isBlank()) {
            return List.of();
        }
        String primary = primaryArtist(artistName);
        String url = "?method=artist.gettoptags&artist=" + encode(primary)
                + "&api_key=" + encode(apiKey) + "&format=json&autocorrect=1";
        return callAndParseTopTags(url, "artist.gettoptags '" + primary + "'");
    }

    /**
     * Obtiene los tags específicos de una canción desde Last.fm (endpoint `track.gettoptags`).
     * Mucho más rico que los tags del artista: incluye señales de idioma ("spanish",
     * "paraguayo"), mood ("ballad", "acoustic"), época, y tags de género a nivel canción.
     *
     * Last.fm es una base comunitaria — una misma canción puede estar registrada bajo
     * varias formas de su artistado (ej: "Rei", "Rei, Callejero Fino", "Rei & Callejero Fino").
     * Los tags ricos pueden estar en cualquiera de esas entradas. Probamos las variaciones
     * más comunes y combinamos los tags deduplicados para maximizar la cobertura.
     *
     * Retorna lista vacía si ninguna variación devuelve resultados o si la llamada falla.
     */
    public List<String> fetchTrackTags(String artistName, String trackName) {
        if (!isConfigured() || artistName == null || artistName.isBlank()
                || trackName == null || trackName.isBlank()) {
            return List.of();
        }
        List<String> variations = buildArtistVariations(artistName);
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        for (String variation : variations) {
            String url = "?method=track.gettoptags&artist=" + encode(variation)
                    + "&track=" + encode(trackName)
                    + "&api_key=" + encode(apiKey) + "&format=json&autocorrect=1";
            List<String> tags = callAndParseTopTags(url,
                    "track.gettoptags '" + trackName + "' - '" + variation + "'");
            combined.addAll(tags);
        }
        return new ArrayList<>(combined);
    }

    // Dada la cadena de artistas de Spotify ("Rei, Callejero Fino"), devuelve las formas
    // en que Last.fm puede tener registrada a esta colaboración. Cada variación es un
    // request extra a Last.fm, pero las 4 más habituales:
    //   1. Primer artista solo ("Rei") — Last.fm a veces tiene la entrada bajo el "líder".
    //   2. Todos juntos con ", " ("Rei, Callejero Fino") — match directo al CSV de Spotify.
    //   3. Todos juntos con " & " ("Rei & Callejero Fino") — separador muy común en Last.fm.
    //   4. Todos juntos con " y " ("Rei y Callejero Fino") — variante en español.
    // Si ninguna variación devuelve tags, el caller cae al fallback de tags a nivel artista
    // (ver ClassificationService.prefetchSongTags fase 2).
    // Para canciones de un solo artista las 4 variaciones colapsan en una sola (1 request).
    private List<String> buildArtistVariations(String artistCsv) {
        List<String> parts = Arrays.stream(artistCsv.split(",\\s*"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (parts.isEmpty()) return List.of();
        if (parts.size() == 1) return List.of(parts.get(0));

        LinkedHashSet<String> variations = new LinkedHashSet<>();
        variations.add(parts.get(0));
        variations.add(String.join(", ", parts));
        variations.add(String.join(" & ", parts));
        variations.add(String.join(" y ", parts));
        return new ArrayList<>(variations);
    }

    // Método compartido: ambos endpoints (artist.gettoptags y track.gettoptags) devuelven
    // el mismo formato {"toptags": {"tag": [{"name": ..., "count": ...}, ...]}}
    private List<String> callAndParseTopTags(String url, String logContext) {
        try {
            String body = client.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { /* silencio */ })
                    .body(String.class);
            if (body == null || body.isBlank()) return List.of();
            JsonNode root = objectMapper.readTree(body);

            // Last.fm devuelve errores con {"error": N, "message": "..."} en 200 OK
            if (root.has("error")) {
                log.debug("Last.fm {}: error {}", logContext, root.path("message").asText());
                return List.of();
            }

            JsonNode tagsArray = root.path("toptags").path("tag");
            if (!tagsArray.isArray()) return List.of();

            List<String> tags = new ArrayList<>();
            int count = 0;
            for (JsonNode tagNode : tagsArray) {
                if (count >= MAX_TAGS_PER_ARTIST) break;
                String name = tagNode.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    tags.add(name.trim());
                    count++;
                }
            }
            return tags;
        } catch (Exception e) {
            log.debug("Last.fm {} falló: {}", logContext, e.getMessage());
            return List.of();
        }
    }

    private String primaryArtist(String artist) {
        int comma = artist.indexOf(',');
        return (comma > 0 ? artist.substring(0, comma) : artist).trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
