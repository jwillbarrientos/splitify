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
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClassificationService {

    private final SongRepository songRepository;
    private final SongClassificationRepository classificationRepository;
    private final ObjectMapper objectMapper;
    private final RestClient openAiClient;
    private final String apiKey;

    private static final Set<String> VALID_GENRES = Set.of(
            "Pop",
            "Rock",
            "Hip-Hop/Rap",
            "Electrónica/Dance",
            "R&B/Soul/Funk",
            "Jazz/Blues",
            "Clásica/Orquestal",
            "Reggae/Ska/World",
            "Country/Folk",
            "Latina/Urbano"
    );

    private static final String SYSTEM_PROMPT = """
            Eres un experto en música. Clasifica cada canción según REGLAS ESTRICTAS.

            GÉNERO MUSICAL — DEBE ser EXACTAMENTE uno de estos 10 valores, copiado literalmente:
            1. Pop
            2. Rock
            3. Hip-Hop/Rap
            4. Electrónica/Dance
            5. R&B/Soul/Funk
            6. Jazz/Blues
            7. Clásica/Orquestal
            8. Reggae/Ska/World
            9. Country/Folk
            10. Latina/Urbano

            REGLAS OBLIGATORIAS:
            - PROHIBIDO inventar géneros o subgéneros.
            - PROHIBIDO devolver "Indefinida", "Desconocido", "Otro", "N/A" o variaciones.
            - TODA canción encaja en UNO de los 10. Si dudas, elige el más cercano.
            - Mapeo obligatorio de subgéneros comunes:
              * Vallenato, Cumbia, Salsa, Reggaeton, Bachata, Merengue, Tango, Mariachi, Bolero, Ranchera, Banda, Trap latino, Dembow → Latina/Urbano
              * Metal, Punk, Grunge, Hard rock, Alternative, Indie rock → Rock
              * Trap, Drill, Rap, Boom-bap, Conscious hip-hop → Hip-Hop/Rap
              * House, Techno, EDM, Dubstep, Trance, Drum and Bass, Synthwave → Electrónica/Dance
              * Soul, Funk, R&B, Motown, Disco, Neo-soul → R&B/Soul/Funk
              * Jazz, Blues, Swing, Bebop, Bossa nova jazz → Jazz/Blues
              * Sinfónica, Ópera, Barroca, Música clásica, Banda sonora orquestal → Clásica/Orquestal
              * Reggae, Ska, Dancehall, Afrobeat, K-pop, J-pop, Música del mundo, Folclore no latino → Reggae/Ska/World
              * Country, Folk, Bluegrass, Americana → Country/Folk
              * Todo lo demás melódico/comercial (incluida música infantil, baladas genéricas) → Pop

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
    // Intentos del bucle de persistencia: tras cada barrido por lotes, las canciones que
    // quedan con genre=null (por excepción o respuesta incompleta sin excepción) se reintentan.
    // Hasta MAX_PERSISTENCE_ATTEMPTS × MAX_RETRIES = 10 llamadas reales por canción durante sync.
    private static final int MAX_PERSISTENCE_ATTEMPTS = 5;

    /**
     * Clasifica género e idioma de canciones usando ChatGPT (en lotes con reintento).
     * Primero consulta el caché global; solo envía a ChatGPT las canciones no cacheadas.
     * Usa el límite por defecto de intentos ({@value #MAX_PERSISTENCE_ATTEMPTS}).
     */
    public void classifySongs(List<Song> songs) {
        classifySongs(songs, MAX_PERSISTENCE_ATTEMPTS);
    }

    /**
     * Variante con límite configurable de intentos. Se usa desde el "gate" de creación de
     * playlists, donde queremos un presupuesto más chico (ej: 2) para mantener el flujo responsivo
     * y fallar rápido con 422 si ChatGPT sigue sin responder.
     */
    public void classifySongs(List<Song> songs, int maxAttempts) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY no configurada, saltando clasificación");
            return;
        }

        // Invalidar géneros no permitidos: borrar del caché y limpiar la canción
        // para que se re-clasifique con el prompt corregido.
        for (Song song : songs) {
            if (song.getGenre() != null && !VALID_GENRES.contains(song.getGenre())) {
                log.info("Género inválido '{}' en '{}' - {}, se re-clasificará",
                        song.getGenre(), song.getName(), song.getArtist());
                song.setGenre(null);
                song.setLanguage(null);
                songRepository.save(song);
                classificationRepository.findBySpotifyId(song.getSpotifyId())
                        .ifPresent(classificationRepository::delete);
            }
        }

        // Aplicar caché global antes de filtrar (ignorando entradas con género inválido)
        for (Song song : songs) {
            if (song.getGenre() != null) continue;
            Optional<SongClassification> cached = classificationRepository.findBySpotifyId(song.getSpotifyId());
            if (cached.isPresent() && VALID_GENRES.contains(cached.get().getGenre())) {
                song.setGenre(cached.get().getGenre());
                song.setLanguage(cached.get().getLanguage());
                songRepository.save(song);
            } else if (cached.isPresent()) {
                classificationRepository.delete(cached.get());
            }
        }

        List<Song> pending = songs.stream()
                .filter(s -> s.getGenre() == null)
                .collect(Collectors.toCollection(ArrayList::new));

        if (pending.isEmpty()) {
            return;
        }

        int initial = pending.size();
        log.info("Clasificando género e idioma de {} canciones con ChatGPT (hasta {} pasadas)...",
                initial, maxAttempts);

        int attempt = 1;
        while (!pending.isEmpty() && attempt <= maxAttempts) {
            for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
                List<Song> batch = pending.subList(i, Math.min(i + BATCH_SIZE, pending.size()));
                classifyBatchWithRetry(batch);
            }
            List<Song> stillPending = pending.stream()
                    .filter(s -> s.getGenre() == null)
                    .collect(Collectors.toCollection(ArrayList::new));
            int resolvedThisPass = pending.size() - stillPending.size();
            if (resolvedThisPass > 0) {
                log.info("Pasada {}/{}: {} canciones clasificadas, quedan {}.",
                        attempt, maxAttempts, resolvedThisPass, stillPending.size());
            } else if (!stillPending.isEmpty()) {
                log.warn("Pasada {}/{} sin progreso: {} canciones siguen sin clasificar.",
                        attempt, maxAttempts, stillPending.size());
            }
            pending = stillPending;
            attempt++;
        }

        if (pending.isEmpty()) {
            log.info("Clasificación completada para {} canciones", initial);
        } else {
            log.warn("Tras {} intentos quedaron {} canciones sin clasificar (de {}).",
                    maxAttempts, pending.size(), initial);
        }
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
            if (!VALID_GENRES.contains(genre)) {
                log.warn("ChatGPT devolvió género inválido '{}' para '{}' - {}. No se guarda; se reintentará en el próximo sync.",
                        genre, song.getName(), song.getArtist());
                continue;
            }
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
