package io.jona.smusic.sorted_music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jona.smusic.sorted_music.model.Song;
import io.jona.smusic.sorted_music.repository.SongRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ClassificationService {

    private final SongRepository songRepository;
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
                                 @Value("${openai.api-key:}") String apiKey) {
        this.songRepository = songRepository;
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.openAiClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    /**
     * Clasifica género e idioma de canciones usando ChatGPT.
     */
    public void classifySongs(List<Song> songs) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY no configurada, saltando clasificación");
            return;
        }

        List<Song> unclassified = songs.stream()
                .filter(s -> s.getGenre() == null)
                .toList();

        if (unclassified.isEmpty()) {
            return;
        }

        log.info("Clasificando género e idioma de {} canciones con ChatGPT...", unclassified.size());

        StringBuilder userPrompt = new StringBuilder("Clasifica estas canciones:\n");
        for (int i = 0; i < unclassified.size(); i++) {
            Song song = unclassified.get(i);
            userPrompt.append(String.format("%d. \"%s\" de %s\n", i, song.getName(), song.getArtist()));
        }

        try {
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
                if (index < 0 || index >= unclassified.size()) continue;

                Song song = unclassified.get(index);
                song.setGenre(songNode.path("genre").asText());

                List<String> langs = new ArrayList<>();
                for (JsonNode lang : songNode.path("languages")) {
                    langs.add(lang.asText());
                }
                song.setLanguage(String.join(", ", langs));

                songRepository.save(song);
            }

            log.info("Clasificación completada para {} canciones", unclassified.size());
        } catch (Exception e) {
            log.error("Error clasificando canciones con ChatGPT: {}", e.getMessage());
        }
    }
}
