package io.jona.smusic.sorted_music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jona.smusic.sorted_music.model.Song;
import io.jona.smusic.sorted_music.model.SongClassification;
import io.jona.smusic.sorted_music.repository.SongClassificationRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ClassificationService {

    private final SongRepository songRepository;
    private final SongClassificationRepository classificationRepository;
    private final ObjectMapper objectMapper;
    private final RestClient openAiClient;
    private final String apiKey;

    private static final String SYSTEM_PROMPT = """
            Eres un experto en música. Para cada canción proporciona género e idioma(s).

            GÉNERO MUSICAL (exactamente uno):
            Pop, Rock, Hip-Hop/Rap, Electrónica/Dance, R&B/Soul/Funk, Jazz/Blues, Clásica/Orquestal, Reggae/Ska/World, Country/Folk, Latina/Urbano

            IDIOMA(S):
            - Detecta el/los idioma(s) reales de la letra.
            - Instrumental o sin letra → "Sin letra".
            - Canción multilingüe → lista todos los idiomas.

            Responde SOLO con JSON válido:
            {"songs":[{"index":0,"genre":"Rock","languages":["Inglés"]}]}
            """;

    public ClassificationService(SongRepository songRepository,
                                 SongClassificationRepository classificationRepository,
                                 @Value("${openai.api-key:}") String apiKey) {
        this.songRepository = songRepository;
        this.classificationRepository = classificationRepository;
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.openAiClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 2;

    /**
     * Clasifica género e idioma de canciones usando ChatGPT (en lotes con reintento).
     * Primero consulta el caché global; solo envía a ChatGPT las canciones no cacheadas.
     */
    public void classifySongs(List<Song> songs) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY no configurada, saltando clasificación");
            return;
        }

        // Aplicar caché global antes de filtrar
        for (Song song : songs) {
            if (song.getGenre() != null) continue;
            Optional<SongClassification> cached = classificationRepository.findBySpotifyId(song.getSpotifyId());
            if (cached.isPresent()) {
                song.setGenre(cached.get().getGenre());
                song.setLanguage(cached.get().getLanguage());
                songRepository.save(song);
            }
        }

        List<Song> unclassified = songs.stream()
                .filter(s -> s.getGenre() == null)
                .toList();

        if (unclassified.isEmpty()) {
            return;
        }

        log.info("Clasificando género e idioma de {} canciones con ChatGPT...", unclassified.size());

        for (int i = 0; i < unclassified.size(); i += BATCH_SIZE) {
            List<Song> batch = unclassified.subList(i, Math.min(i + BATCH_SIZE, unclassified.size()));
            classifyBatchWithRetry(batch);
        }

        log.info("Clasificación completada para {} canciones", unclassified.size());
    }

    private void classifyBatchWithRetry(List<Song> batch) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                classifyBatch(batch);
                return;
            } catch (Exception e) {
                log.warn("Intento {}/{} fallido clasificando lote de {} canciones: {}",
                        attempt, MAX_RETRIES, batch.size(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    log.error("Lote de {} canciones no clasificado tras {} intentos", batch.size(), MAX_RETRIES);
                }
            }
        }
    }

    private void classifyBatch(List<Song> batch) throws Exception {
        StringBuilder userPrompt = new StringBuilder("Clasifica estas canciones:\n");
        for (int i = 0; i < batch.size(); i++) {
            Song song = batch.get(i);
            userPrompt.append(String.format("%d. \"%s\" de %s\n", i, song.getName(), song.getArtist()));
        }

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "gpt-4.1-nano",
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt.toString())
                ),
                "response_format", Map.of("type", "json_object")
        ));

        String response = openAiClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode classification = objectMapper.readTree(content);
        JsonNode songsNode = classification.path("songs");

        for (JsonNode songNode : songsNode) {
            int index = songNode.path("index").asInt();
            if (index < 0 || index >= batch.size()) continue;

            Song song = batch.get(index);
            String genre = songNode.path("genre").asText();
            List<String> langs = new ArrayList<>();
            for (JsonNode lang : songNode.path("languages")) {
                langs.add(lang.asText());
            }
            String language = String.join(", ", langs);

            song.setGenre(genre);
            song.setLanguage(language);
            songRepository.save(song);

            // Guardar en caché global
            if (classificationRepository.findBySpotifyId(song.getSpotifyId()).isEmpty()) {
                classificationRepository.save(SongClassification.builder()
                        .spotifyId(song.getSpotifyId())
                        .genre(genre)
                        .language(language)
                        .build());
            }
        }
    }
}
