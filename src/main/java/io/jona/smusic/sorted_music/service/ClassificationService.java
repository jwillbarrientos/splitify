package io.jona.smusic.sorted_music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jona.smusic.sorted_music.model.ArtistGenres;
import io.jona.smusic.sorted_music.model.Song;
import io.jona.smusic.sorted_music.model.SongClassification;
import io.jona.smusic.sorted_music.model.TrackTags;
import io.jona.smusic.sorted_music.repository.ArtistGenresRepository;
import io.jona.smusic.sorted_music.repository.SongClassificationRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import io.jona.smusic.sorted_music.repository.TrackTagsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClassificationService {

    private final SongRepository songRepository;
    private final SongClassificationRepository classificationRepository;
    private final ArtistGenresRepository artistGenresRepository;
    private final TrackTagsRepository trackTagsRepository;
    private final LastFmClient lastFmClient;
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

    // Alias para recuperar typos predecibles de ChatGPT (acentos omitidos, nombres en inglés,
    // subgéneros sueltos). Antes de descartar una respuesta como inválida intentamos mapearla
    // acá — evita reintentos innecesarios. Keys en minúsculas para match case-insensitive.
    private static final Map<String, String> GENRE_ALIASES = buildAliases();

    private static Map<String, String> buildAliases() {
        Map<String, String> m = new HashMap<>();
        for (String k : List.of("latin", "latino", "latina", "latin/urbano", "latino/urbano", "latinia/urbano",
                "urbano", "vallenato", "cumbia", "cumbia/world", "salsa", "merengue", "bachata",
                "reggaeton", "reggaetón", "reggaeton flow", "bolero", "ranchera", "mariachi", "banda",
                "tango", "trap latino", "dembow", "mexican", "mexican pop", "latin pop")) {
            m.put(k, "Latina/Urbano");
        }
        for (String k : List.of("classical", "classica", "clasica", "clásica", "clasica/orquestal",
                "clásica/orquestal", "orchestral", "orchestra", "soundtrack", "score", "film score",
                "trailer", "trailer/score", "trailer music", "video game music", "video game soundtrack",
                "game soundtrack", "symphonic", "sinfonia", "sinfónica", "opera", "ópera",
                "baroque", "barroca", "ambient", "neoclassical", "modern classical", "piano solo")) {
            m.put(k, "Clásica/Orquestal");
        }
        for (String k : List.of("electronic", "electronica", "electrónica", "electronic/dance",
                "electrónica/dance", "electronica/dance", "dance", "edm", "house", "techno", "trance",
                "dubstep", "drum and bass", "synthwave")) {
            m.put(k, "Electrónica/Dance");
        }
        for (String k : List.of("hip-hop", "hip hop", "hiphop", "rap", "trap", "drill", "boom-bap",
                "conscious hip-hop")) {
            m.put(k, "Hip-Hop/Rap");
        }
        for (String k : List.of("r&b", "rnb", "soul", "funk", "r&b/soul", "r&b/soul/funk",
                "motown", "disco", "neo-soul")) {
            m.put(k, "R&B/Soul/Funk");
        }
        for (String k : List.of("jazz", "blues", "swing", "bebop", "bossa nova")) {
            m.put(k, "Jazz/Blues");
        }
        for (String k : List.of("country", "folk", "bluegrass", "americana", "country/folk")) {
            m.put(k, "Country/Folk");
        }
        for (String k : List.of("reggae", "ska", "dancehall", "world", "world music", "reggae/ska/world",
                "k-pop", "j-pop", "afrobeat", "anime", "anime/video game music",
                "anime/video game soundtrack", "j-rock", "k-rock")) {
            m.put(k, "Reggae/Ska/World");
        }
        for (String k : List.of("rock", "metal", "punk", "grunge", "hard rock", "alternative",
                "alternativo", "indie", "indie rock", "indie/alternativo", "indie/alternative",
                "indie alternative")) {
            m.put(k, "Rock");
        }
        for (String k : List.of("pop", "dream pop", "synth pop", "synthpop", "teen pop", "electro pop")) {
            m.put(k, "Pop");
        }
        return Map.copyOf(m);
    }

    private static String canonicalGenre(String raw) {
        if (raw == null) return null;
        if (VALID_GENRES.contains(raw)) return raw;
        return GENRE_ALIASES.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    // Alias para normalizar idiomas devueltos por ChatGPT. Aunque el prompt pide nombres en
    // español, el modelo ocasionalmente devuelve la forma inglesa ("Spanish" en vez de
    // "Español"), lo que generaría playlists duplicadas. Traducimos al español canónico
    // antes de guardar. Keys en minúsculas para match case-insensitive.
    private static final Map<String, String> LANGUAGE_ALIASES = buildLanguageAliases();

    private static Map<String, String> buildLanguageAliases() {
        Map<String, String> m = new HashMap<>();
        m.put("spanish", "Español");
        m.put("castilian", "Español");
        m.put("castellano", "Español");
        m.put("english", "Inglés");
        m.put("ingles", "Inglés");
        m.put("french", "Francés");
        m.put("frances", "Francés");
        m.put("portuguese", "Portugués");
        m.put("portugues", "Portugués");
        m.put("brazilian portuguese", "Portugués");
        m.put("italian", "Italiano");
        m.put("german", "Alemán");
        m.put("aleman", "Alemán");
        m.put("deutsch", "Alemán");
        m.put("japanese", "Japonés");
        m.put("japones", "Japonés");
        m.put("korean", "Coreano");
        m.put("chinese", "Chino");
        m.put("mandarin", "Mandarín");
        m.put("cantonese", "Cantonés");
        m.put("russian", "Ruso");
        m.put("arabic", "Árabe");
        m.put("arabe", "Árabe");
        m.put("hebrew", "Hebreo");
        m.put("turkish", "Turco");
        m.put("dutch", "Holandés");
        m.put("holandes", "Holandés");
        m.put("swedish", "Sueco");
        m.put("norwegian", "Noruego");
        m.put("danish", "Danés");
        m.put("finnish", "Finlandés");
        m.put("polish", "Polaco");
        m.put("czech", "Checo");
        m.put("hungarian", "Húngaro");
        m.put("romanian", "Rumano");
        m.put("bulgarian", "Búlgaro");
        m.put("ukrainian", "Ucraniano");
        m.put("greek", "Griego");
        m.put("catalan", "Catalán");
        m.put("galician", "Gallego");
        m.put("basque", "Vasco");
        m.put("indonesian", "Indonesio");
        m.put("thai", "Tailandés");
        m.put("vietnamese", "Vietnamita");
        m.put("hindi", "Hindi");
        m.put("instrumental", "Sin letra");
        m.put("no lyrics", "Sin letra");
        m.put("sin letras", "Sin letra");
        return Map.copyOf(m);
    }

    // Normaliza un idioma devuelto por ChatGPT: si aparece como alias conocido (ej: "Spanish"),
    // lo convierte al nombre canónico en español ("Español"). Si no hay alias, devuelve el
    // original sin cambios — asumimos que ya está en formato aceptable.
    private static String canonicalLanguage(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return trimmed;
        String alias = LANGUAGE_ALIASES.get(trimmed.toLowerCase(Locale.ROOT));
        return alias != null ? alias : trimmed;
    }

    private static final String SYSTEM_PROMPT = """
            Eres un experto en música. Clasifica cada canción según REGLAS ESTRICTAS.

            Junto al nombre y artista de cada canción, cuando esté disponible, recibirás entre
            corchetes los TAGS DE GÉNERO DE LAST.FM para ese artista (ej: "rock, indie rock,
            alternative"; "reggaeton, latin"; "film score, soundtrack"). Esos tags son señales
            reales curadas por la comunidad — USALOS como fuente principal para decidir el género,
            y solo en ausencia de tags recurre a tu conocimiento general del artista.

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
            - Mapeo obligatorio de subgéneros comunes (incluye tags de Last.fm):
              * Vallenato, Cumbia, Salsa, Reggaeton, Bachata, Merengue, Tango, Mariachi, Bolero,
                Ranchera, Banda, Trap latino, Dembow, Latin, Latin pop → Latina/Urbano
              * Metal, Punk, Grunge, Hard rock, Alternative, Indie rock, Indie → Rock
              * Trap, Drill, Rap, Boom-bap, Conscious hip-hop, Hip-Hop → Hip-Hop/Rap
              * House, Techno, EDM, Dubstep, Trance, Drum and Bass, Synthwave → Electrónica/Dance
              * Soul, Funk, R&B, Motown, Disco, Neo-soul → R&B/Soul/Funk
              * Jazz, Blues, Swing, Bebop, Bossa nova → Jazz/Blues
              * Sinfónica, Ópera, Barroca, Música clásica, Banda sonora orquestal, Soundtrack,
                Score, Film score, Orchestral, Video game music, Trailer music → Clásica/Orquestal
              * Reggae, Ska, Dancehall, Afrobeat, K-pop, J-pop, Música del mundo, Folclore no
                latino, Anime → Reggae/Ska/World
              * Country, Folk, Bluegrass, Americana → Country/Folk
              * Todo lo demás melódico/comercial (incluida música infantil, baladas genéricas,
                dream pop, synth pop) → Pop

            IDIOMA(S):
            - REGLA POR DEFECTO: la canción TIENE letra cantada. "Sin letra" es la
              excepción — úsalo SOLO si estás seguro de que es una pieza instrumental.
            - OBLIGATORIO: SIEMPRE devolver al menos un idioma válido. PROHIBIDO
              devolver "Indefinido", "Desconocido", "N/A", "Otro", "Unknown" o
              similares. El array languages NUNCA puede estar vacío.
            - IMPORTANTE: usa SIEMPRE los nombres de idiomas en ESPAÑOL, nunca en inglés.
              Ejemplos correctos: "Inglés" (NO "English"), "Español" (NO "Spanish"),
              "Francés" (NO "French"), "Portugués" (NO "Portuguese"), "Alemán" (NO
              "German"), "Italiano" (NO "Italian"), "Japonés" (NO "Japanese"),
              "Coreano" (NO "Korean"), "Chino" (NO "Chinese"), "Ruso" (NO "Russian"),
              "Árabe" (NO "Arabic"). Mezclar los dos idiomas genera playlists duplicadas
              y es un error grave.
            - ORDEN DE PRECEDENCIA para decidir el idioma (usá este orden):
              1. Si hay tags de Last.fm con señal geográfica/lingüística explícita
                 (spanish, english, french, brazilian, argentine, paraguayo, k-pop,
                 italiano, etc.) → úsalos.
              2. Si reconoces la canción o al artista, usá tu conocimiento (Bad Bunny
                 → Español; Bob Marley → Inglés; The Beatles → Inglés).
              3. Si NO hay tags útiles Y no reconoces la canción, INFIERE por el
                 TÍTULO: las palabras del título suelen estar en el idioma de la
                 letra (ej: "Rayando el Sol" → Español; "Yesterday" → Inglés; "La
                 Vie en Rose" → Francés; "Tu turrito" → Español).
              4. Si el título también es ambiguo (números, palabra neutral, acrónimo),
                 usar Inglés como último recurso (es el idioma más común en catálogos).
            - Si reconoces al artista como cantante con voz humana, clasifica el
              idioma de la letra, NUNCA "Sin letra" — aunque Last.fm tenga tags
              ambiguos como "piano", "acoustic" o "ambient".
            - "Sin letra" es MUTUAMENTE EXCLUYENTE con otros idiomas: si devuelves
              "Sin letra", el array languages debe tener SOLO ese valor.
            - Usa "Sin letra" únicamente con evidencia clara:
              * Tags DOMINANTES de Last.fm: "soundtrack", "score", "film score",
                "video game music", "orchestral", "classical", "instrumental"
                (palabra exacta, no "piano" ni "acoustic" que son engañosos)
              * Título con: "Theme", "OST", "Main Title", "Score", "Suite",
                "Overture", "Prelude", "Nocturne", "Concerto", "Sonata", "Symphony",
                "Instrumental", "Piano Solo", "Piano Version", "Acoustic Instrumental"
              * Compositores de cine/videojuegos (Hans Zimmer, John Williams, Ludwig
                Göransson, Pedro Bromfman, Ennio Morricone, Joe Hisaishi) cuando el
                título no sugiere voces.
            - Canciones multilingües: SOLO lista varios idiomas si la canción
              REALMENTE alterna entre idiomas. NO agregues idiomas "por si acaso".
            - PROHIBIDO inventar idiomas raros (indonesio, tailandés, suajili, etc.)
              cuando hay duda entre idiomas comunes. Inglés y Español son los más
              comunes; con duda, preferilos antes que cualquier idioma exótico.

            FORMATO DE RESPUESTA (OBLIGATORIO):
            - Responde SOLO con JSON válido, sin explicaciones ni texto extra.
            - CADA canción DEBE incluir AMBOS campos: "genre" (string) y "languages" (array no vacío).
            - NUNCA devolver "languages": [] (array vacío). Si una canción parece no tener idioma,
              usar "Sin letra". Si no estás seguro, elige el idioma más probable según el título.
            - PROHIBIDO devolver un idioma en el campo "genre" (ej: "Japonés" no es un género).

            Ejemplo:
            {"songs":[{"index":0,"genre":"Rock","languages":["Inglés"]},{"index":1,"genre":"Clásica/Orquestal","languages":["Sin letra"]}]}
            """;

    public ClassificationService(SongRepository songRepository,
                                 SongClassificationRepository classificationRepository,
                                 ArtistGenresRepository artistGenresRepository,
                                 TrackTagsRepository trackTagsRepository,
                                 LastFmClient lastFmClient,
                                 @Value("${openai.api-key:}") String apiKey) {
        this.songRepository = songRepository;
        this.classificationRepository = classificationRepository;
        this.artistGenresRepository = artistGenresRepository;
        this.trackTagsRepository = trackTagsRepository;
        this.lastFmClient = lastFmClient;
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

    // Last.fm no tiene endpoint batch (una canción por llamada). Paralelizamos con 5 hilos
    // para respetar el rate limit oficial (5 req/seg por API key) con margen de seguridad.
    private static final int LASTFM_THREADS = 5;

    /**
     * Clasifica género e idioma de canciones usando ChatGPT (en lotes con reintento).
     * Primero consulta el caché global; solo envía a ChatGPT las canciones no cacheadas.
     * Para mejorar la precisión, antes de llamar a ChatGPT trae los tags de Last.fm por artista
     * y los incluye en el prompt como contexto.
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

        // Invalidar géneros no permitidos y entradas de caché parciales/inválidas.
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

        // Aplicar caché global antes de filtrar. Invalidar entradas con:
        //   - Género fuera de la whitelist (resabio de versiones viejas del prompt)
        //   - Idioma en blanco o null (resabio del bug pre-fix donde ChatGPT devolvía [])
        for (Song song : songs) {
            if (song.getGenre() != null && !song.getGenre().isBlank()
                    && song.getLanguage() != null && !song.getLanguage().isBlank()) {
                continue;
            }
            Optional<SongClassification> cached = classificationRepository.findBySpotifyId(song.getSpotifyId());
            if (cached.isEmpty()) continue;

            SongClassification c = cached.get();
            boolean validGenre = c.getGenre() != null && VALID_GENRES.contains(c.getGenre());
            boolean validLanguage = c.getLanguage() != null && !c.getLanguage().isBlank();
            if (validGenre && validLanguage) {
                song.setGenre(c.getGenre());
                song.setLanguage(c.getLanguage());
                songRepository.save(song);
            } else {
                classificationRepository.delete(c);
                song.setGenre(null);
                song.setLanguage(null);
                songRepository.save(song);
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

        // Prefetch de tags de Last.fm por canción (con fallback a tags de artista).
        // Se hace una sola vez antes del bucle porque los tags no cambian entre pasadas.
        Map<String, List<String>> tagsBySpotifyId = prefetchSongTags(pending);

        int attempt = 1;
        while (!pending.isEmpty() && attempt <= maxAttempts) {
            for (int i = 0; i < pending.size(); i += BATCH_SIZE) {
                List<Song> batch = pending.subList(i, Math.min(i + BATCH_SIZE, pending.size()));
                classifyBatchWithRetry(batch, tagsBySpotifyId);
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

    // Devuelve un mapa spotifyTrackId → [tags efectivos] para las canciones pending.
    // Flujo:
    //   1. Prefetch de tags POR CANCIÓN (track.gettoptags) — tags más ricos y específicos.
    //      Incluyen señales directas de idioma ("spanish", "paraguayo") y de género fino.
    //   2. Para canciones donde Last.fm no tuvo tags (típicamente pistas oscuras),
    //      fallback a tags POR ARTISTA (artist.gettoptags) como señal más débil.
    //   3. Ambos tipos se cachean en sus tablas respectivas (TrackTags y ArtistGenres)
    //      incluso cuando están vacíos, para no re-consultar en syncs posteriores.
    // Si LASTFM_API_KEY no está configurada, solo se usa lo cacheado y se sigue sin tags.
    private Map<String, List<String>> prefetchSongTags(List<Song> songs) {
        Map<String, List<String>> result = new HashMap<>();

        // ========== FASE 1: Track tags (por canción) ==========
        List<String> allTrackIds = songs.stream()
                .map(Song::getSpotifyId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        // Lookup cache de track tags
        Set<String> cachedTrackIds = new HashSet<>();
        if (!allTrackIds.isEmpty()) {
            List<TrackTags> cached = trackTagsRepository.findBySpotifyTrackIdIn(allTrackIds);
            for (TrackTags tt : cached) {
                cachedTrackIds.add(tt.getSpotifyTrackId());
                List<String> tags = splitGenres(tt.getTags());
                if (!tags.isEmpty()) {
                    result.put(tt.getSpotifyTrackId(), tags);
                }
            }
        }

        List<Song> missingTrackTags = songs.stream()
                .filter(s -> s.getSpotifyId() != null && !cachedTrackIds.contains(s.getSpotifyId()))
                .toList();

        if (!missingTrackTags.isEmpty() && lastFmClient.isConfigured()) {
            log.info("Consultando tags de {} canciones a Last.fm (track.gettoptags, {} hilos)...",
                    missingTrackTags.size(), LASTFM_THREADS);
            try (ExecutorService executor = Executors.newFixedThreadPool(LASTFM_THREADS)) {
                List<CompletableFuture<List<String>>> futures = missingTrackTags.stream()
                        .map(song -> CompletableFuture.supplyAsync(
                                () -> lastFmClient.fetchTrackTags(song.getArtist(), song.getName()),
                                executor))
                        .toList();

                int total = missingTrackTags.size();
                for (int i = 0; i < futures.size(); i++) {
                    Song song = missingTrackTags.get(i);
                    List<String> tags;
                    try {
                        tags = futures.get(i).join();
                    } catch (Exception e) {
                        log.debug("Error track tags '{}': {}", song.getName(), e.getMessage());
                        tags = List.of();
                    }
                    trackTagsRepository.save(TrackTags.builder()
                            .spotifyTrackId(song.getSpotifyId())
                            .tags(String.join(", ", tags))
                            .build());
                    if (!tags.isEmpty()) {
                        result.put(song.getSpotifyId(), tags);
                    }

                    int done = i + 1;
                    if (done % 50 == 0 || done == total) {
                        log.info("Track tags: {}/{}", done, total);
                    }
                }
            }
        } else if (!lastFmClient.isConfigured()) {
            log.warn("LASTFM_API_KEY no configurada — se clasificará sin tags");
            return result;
        }

        // ========== FASE 2: Fallback con tags de TODOS los artistas de la canción ==========
        // Para canciones sin track tags, usamos los tags de TODOS sus artistas combinados.
        // Consultar solo el primer artista es problemático cuando ese nombre es ambiguo
        // (ej: "Rei" matchea con un artista japonés popular; en "Rei, Callejero Fino" los
        // tags de Callejero Fino desambiguan). Combinamos los sets para ChatGPT.
        List<Song> needsArtistFallback = songs.stream()
                .filter(s -> s.getSpotifyId() != null && !result.containsKey(s.getSpotifyId()))
                .toList();
        if (needsArtistFallback.isEmpty()) return result;

        // Juntar TODOS los nombres únicos de artistas (no solo el primero) de las canciones
        Set<String> allFallbackArtists = new LinkedHashSet<>();
        for (Song s : needsArtistFallback) {
            allFallbackArtists.addAll(allArtists(s.getArtist()));
        }
        if (allFallbackArtists.isEmpty()) return result;

        // Lookup cache de artist tags
        Map<String, List<String>> artistTagsByName = new HashMap<>();
        List<ArtistGenres> cachedArtists = artistGenresRepository.findByArtistNameIn(new ArrayList<>(allFallbackArtists));
        Set<String> cachedArtistNames = new HashSet<>();
        for (ArtistGenres ag : cachedArtists) {
            cachedArtistNames.add(ag.getArtistName());
            artistTagsByName.put(ag.getArtistName(), splitGenres(ag.getGenres()));
        }

        // Artistas faltantes → consultar Last.fm en paralelo
        List<String> missingArtists = allFallbackArtists.stream()
                .filter(name -> !cachedArtistNames.contains(name))
                .toList();
        if (!missingArtists.isEmpty()) {
            log.info("Fallback: consultando tags de {} artistas a Last.fm (artist.gettoptags)...",
                    missingArtists.size());
            try (ExecutorService executor = Executors.newFixedThreadPool(LASTFM_THREADS)) {
                List<CompletableFuture<List<String>>> futures = missingArtists.stream()
                        .map(name -> CompletableFuture.supplyAsync(
                                () -> lastFmClient.fetchTopTags(name), executor))
                        .toList();
                for (int i = 0; i < futures.size(); i++) {
                    String name = missingArtists.get(i);
                    List<String> tags;
                    try {
                        tags = futures.get(i).join();
                    } catch (Exception e) {
                        tags = List.of();
                    }
                    artistGenresRepository.save(ArtistGenres.builder()
                            .artistName(name)
                            .genres(String.join(", ", tags))
                            .build());
                    artistTagsByName.put(name, tags);
                }
            }
        }

        // Combinar tags de TODOS los artistas de cada canción (deduplicados) y asignar
        for (Song s : needsArtistFallback) {
            LinkedHashSet<String> combined = new LinkedHashSet<>();
            for (String artistName : allArtists(s.getArtist())) {
                List<String> tags = artistTagsByName.getOrDefault(artistName, List.of());
                combined.addAll(tags);
            }
            if (!combined.isEmpty()) {
                result.put(s.getSpotifyId(), new ArrayList<>(combined));
            }
        }
        return result;
    }

    // Devuelve la lista de artistas individuales de una cadena CSV (ej: "Rei, Callejero Fino"
    // → ["Rei", "Callejero Fino"]). Se usa en el fallback para combinar tags de todos los
    // artistas de una colaboración — el primer artista solo puede ser ambiguo (nombre corto
    // compartido con otros artistas populares), pero la combinación suele ser unívoca.
    private List<String> allArtists(String artist) {
        if (artist == null || artist.isBlank()) return List.of();
        return Arrays.stream(artist.split(",\\s*"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> splitGenres(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",\\s*"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private void classifyBatchWithRetry(List<Song> batch, Map<String, List<String>> tagsBySpotifyId) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                classifyBatch(batch, tagsBySpotifyId);
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

    private void classifyBatch(List<Song> batch, Map<String, List<String>> tagsBySpotifyId) throws Exception {
        StringBuilder userPrompt = new StringBuilder("Clasifica estas canciones:\n");
        for (int i = 0; i < batch.size(); i++) {
            Song song = batch.get(i);
            // Tags efectivos: por canción (preferido) o por artista (fallback) — ya resueltos
            // en prefetchSongTags y guardados en el mapa por spotifyId.
            List<String> tags = tagsBySpotifyId.getOrDefault(song.getSpotifyId(), List.of());
            String hint = tags.isEmpty()
                    ? ""
                    : " [tags de Last.fm: " + String.join(", ", tags) + "]";
            userPrompt.append(String.format("%d. \"%s\" de %s%s%n",
                    i, song.getName(), song.getArtist(), hint));
        }

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", "gpt-4.1-mini",
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
            String rawGenre = songNode.path("genre").asText();
            String genre = canonicalGenre(rawGenre);
            if (genre == null) {
                log.warn("ChatGPT devolvió género inválido '{}' para '{}' - {}. No se guarda; se reintentará en el próximo sync.",
                        rawGenre, song.getName(), song.getArtist());
                continue;
            }
            if (!rawGenre.equals(genre)) {
                log.info("Alias de género '{}' → '{}' para '{}' - {}",
                        rawGenre, genre, song.getName(), song.getArtist());
            }

            // Normalizar a nombres canónicos en español y deduplicar. Sin esto, ChatGPT
            // puede devolver ["Spanish", "Español"] por error y terminar generando dos
            // playlists duplicadas para el mismo idioma.
            LinkedHashSet<String> langSet = new LinkedHashSet<>();
            for (JsonNode lang : songNode.path("languages")) {
                String langText = lang.asText();
                if (langText == null || langText.isBlank()) continue;
                String canonical = canonicalLanguage(langText);
                if (canonical != null && !canonical.isBlank()) {
                    langSet.add(canonical);
                }
            }
            if (langSet.isEmpty()) {
                log.warn("ChatGPT no devolvió idiomas para '{}' - {}. No se guarda; se reintentará.",
                        song.getName(), song.getArtist());
                continue;
            }
            String language = String.join(", ", langSet);

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
