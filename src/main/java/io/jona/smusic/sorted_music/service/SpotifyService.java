package io.jona.smusic.sorted_music.service;

import io.jona.smusic.sorted_music.dto.AvailableFiltersDto;
import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.RefreshPreviewDto;
import io.jona.smusic.sorted_music.dto.SongDto;
import io.jona.smusic.sorted_music.dto.SpotifyDto;
import io.jona.smusic.sorted_music.dto.UserProfileDto;
import io.jona.smusic.sorted_music.model.Playlist;
import io.jona.smusic.sorted_music.model.Song;
import io.jona.smusic.sorted_music.model.SplitifyPlaylistSource;
import io.jona.smusic.sorted_music.repository.PlaylistRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import io.jona.smusic.sorted_music.repository.SplitifyPlaylistSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyService {

    private final SpotifyApiClient spotifyApi;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final SplitifyPlaylistSourceRepository sourceRepository;
    private final ClassificationService classificationService;

    // ──── Public API ────────────────────────────────────────────────

    // Obtiene nombre y foto de perfil del usuario autenticado desde la API de Spotify.
    public UserProfileDto getUserProfile(OAuth2AuthenticationToken authentication) {
        RestClient restClient = spotifyApi.createRestClient(authentication);
        SpotifyDto.UserProfileResponse profile = spotifyApi.getUserProfile(restClient);
        return new UserProfileDto(
                profile != null ? profile.displayName() : null,
                extractImageUrl(profile != null ? profile.images() : null)
        );
    }

    // Sincronización principal: trae todas las playlists del usuario desde Spotify,
    // las compara con lo que ya tenemos en BD, y actualiza/crea/elimina según corresponda.
    // Flujo: Liked Songs → playlists propias/colaborativas → limpieza de eliminadas.
    public List<PlaylistDto> syncPlaylists(OAuth2AuthenticationToken authentication) {
        String userId = authentication.getName();
        RestClient restClient = spotifyApi.createRestClient(authentication);

        // Indexar playlists no-Splitify que ya tenemos en BD para comparar con Spotify
        Map<String, Playlist> existingBySpotifyId = indexExistingPlaylists(userId);
        // IDs de playlists creadas por Splitify, para no re-sincronizarlas como si fueran del usuario
        Set<String> splitifySpotifyIds = getSplitifySpotifyIds(userId);

        List<Playlist> savedPlaylists = new ArrayList<>();
        // allSpotifyIds: todas las playlists que existen en Spotify (para detectar Splitify huérfanas)
        Set<String> allSpotifyIds = new HashSet<>();
        // seenSpotifyIds: playlists que procesamos en este sync (para detectar eliminadas del usuario)
        Set<String> seenSpotifyIds = new HashSet<>();

        // 1. Liked Songs se trata como playlist especial con ID ficticio "liked_songs"
        Playlist existingLiked = existingBySpotifyId.remove("liked_songs");
        savedPlaylists.add(syncLikedSongs(restClient, userId, existingLiked));
        seenSpotifyIds.add("liked_songs");

        // 2. Iterar sobre todas las playlists del usuario en Spotify
        for (SpotifyDto.PlaylistItem item : spotifyApi.fetchAllUserPlaylists(restClient)) {
            allSpotifyIds.add(item.id());
            // Ignorar playlists que el usuario solo sigue (no puede acceder a sus tracks en modo dev)
            if (!isOwnedOrCollaborative(item, userId)) continue;
            // Ignorar playlists que Splitify creó (evita sincronizar nuestras propias creaciones)
            if (splitifySpotifyIds.contains(item.id())) continue;

            seenSpotifyIds.add(item.id());
            Playlist existing = existingBySpotifyId.get(item.id());

            // Optimización: si el snapshotId no cambió, la playlist no fue modificada en Spotify.
            // Solo actualizamos metadatos (nombre, imagen) sin re-descargar canciones.
            if (isUnchanged(existing, item)) {
                updatePlaylistMetadata(existing, item);
                savedPlaylists.add(existing);
                log.debug("Playlist '{}' sin cambios (snapshot igual), saltando", item.name());
                continue;
            }

            // La playlist cambió o es nueva: crear/actualizar en BD y descargar sus canciones
            Playlist playlist = upsertPlaylist(existing, item, userId);
            syncPlaylistTracks(restClient, playlist);
            savedPlaylists.add(playlist);
        }

        // 3. Playlists que teníamos en BD pero ya no aparecen en Spotify → eliminar de BD
        removeDeletedPlaylists(existingBySpotifyId, seenSpotifyIds);

        // 4. Playlists Splitify cuyo ID ya no existe en Spotify (el usuario las borró desde Spotify)
        removeOrphanedSplitifyPlaylists(userId, allSpotifyIds);

        return savedPlaylists.stream().map(this::toDto).collect(Collectors.toList());
    }

    // Elimina todas las playlists no-Splitify del usuario de la BD (usado al cerrar sesión/limpiar datos).
    @Transactional
    public void deleteUserPlaylists(String userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, false);
        for (Playlist p : playlists) {
            songRepository.deleteByPlaylistId(p.getId());
        }
        playlistRepository.deleteAll(playlists);
    }

    public List<PlaylistDto> getUserPlaylists(String userId) {
        return playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, false).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PlaylistDto> getSplitifyPlaylists(String userId) {
        return playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, true).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // Vista previa de qué cambiaría al actualizar una playlist Splitify. No modifica nada.
    // Compara 3 fuentes de verdad: lo que hay en Spotify ahora, lo que tenemos en BD, y lo que
    // las playlists origen producirían con el filtro original. Devuelve canciones nuevas y eliminadas.
    public RefreshPreviewDto previewRefresh(OAuth2AuthenticationToken authentication, Long id) {
        String userId = authentication.getName();
        Playlist splitifyPlaylist = findSplitifyPlaylist(id, userId);
        RestClient restClient = spotifyApi.createRestClient(authentication);

        // Leer las canciones actuales de la playlist en Spotify (puede haber sido editada por el usuario)
        List<Song> spotifySongs;
        try {
            spotifySongs = fetchSongsFromSpotify(restClient, splitifyPlaylist.getSpotifyId());
        } catch (Exception e) {
            // Si la playlist ya no existe en Spotify, indicar que hay cambios (se eliminará al refrescar)
            return new RefreshPreviewDto(List.of(), List.of(), true);
        }

        Set<String> spotifyIds = toSpotifyIdSet(spotifySongs);
        // Lo que teníamos guardado en BD la última vez que sincronizamos esta playlist
        List<Song> dbSongs = songRepository.findByPlaylistId(id);
        Set<String> dbSongIds = toSpotifyIdSet(dbSongs);
        // Canciones que las playlists origen producen ahora (aplicando el filtro original)
        List<Song> filteredSourceSongs = getFilteredSourceSongs(id, splitifyPlaylist);
        Set<String> sourceIds = toSpotifyIdSet(filteredSourceSongs);

        // Canciones que el usuario quitó manualmente de la playlist en Spotify:
        // estaban en BD (no excluidas), ya no están en Spotify, pero siguen en las fuentes
        List<SongDto> removedByUser = dbSongs.stream()
                .filter(s -> !s.isExcluded() && !spotifyIds.contains(s.getSpotifyId())
                        && sourceIds.contains(s.getSpotifyId()))
                .map(this::toSongDto)
                .toList();

        // Canciones nuevas de las fuentes: están en las fuentes pero no en Spotify ni en BD
        List<SongDto> newFromSources = filteredSourceSongs.stream()
                .filter(s -> !spotifyIds.contains(s.getSpotifyId())
                        && !dbSongIds.contains(s.getSpotifyId()))
                .map(this::toSongDto)
                .toList();

        // Detectar si el usuario agregó canciones manualmente en Spotify (están en Spotify pero no en BD)
        boolean hasManualAdds = spotifySongs.stream()
                .anyMatch(s -> !dbSongIds.contains(s.getSpotifyId()));
        boolean hasChanges = !removedByUser.isEmpty() || !newFromSources.isEmpty() || hasManualAdds;

        return new RefreshPreviewDto(newFromSources, removedByUser, hasChanges);
    }

    // Actualización real de una playlist Splitify. Reconcilia 3 fuentes de verdad:
    //   - spotifySongs: lo que tiene la playlist ahora mismo en Spotify (incluye ediciones manuales del usuario)
    //   - dbSongs: lo que teníamos guardado en BD del último sync
    //   - filteredSourceSongs: lo que las playlists origen producen ahora con el filtro original
    // El flag restoreRemoved controla si se re-agregan canciones que el usuario quitó manualmente.
    @Transactional
    public PlaylistDto refreshSplitifyPlaylist(OAuth2AuthenticationToken authentication,
                                                Long id, boolean restoreRemoved) {
        String userId = authentication.getName();
        Playlist splitifyPlaylist = findSplitifyPlaylist(id, userId);
        RestClient restClient = spotifyApi.createRestClient(authentication);

        // 1. Leer estado actual en Spotify (puede fallar si el usuario la borró desde Spotify)
        List<Song> spotifySongs;
        try {
            spotifySongs = fetchSongsFromSpotify(restClient, splitifyPlaylist.getSpotifyId());
        } catch (Exception e) {
            log.info("Playlist de Splitify '{}' ya no existe en Spotify, eliminando de BD",
                    splitifyPlaylist.getName());
            deleteSplitifyPlaylistCascade(id);
            return null;
        }

        Set<String> spotifyIds = toSpotifyIdSet(spotifySongs);
        List<Song> dbSongs = songRepository.findByPlaylistId(id);
        Map<String, Song> dbById = indexBySpotifyId(dbSongs);
        Set<String> dbSongIds = dbById.keySet();
        // IDs de canciones que ya estaban marcadas como excluidas (el usuario las quitó antes)
        Set<String> previouslyExcluded = getExcludedIds(dbSongs);

        // 2. Obtener canciones actuales de las playlists origen, aplicando el filtro original
        List<Song> filteredSourceSongs = getFilteredSourceSongs(id, splitifyPlaylist);
        Set<String> sourceIds = toSpotifyIdSet(filteredSourceSongs);

        // 3. Construir la lista final mezclando las 3 fuentes (ver buildFinalSongList para detalle)
        Map<String, Song> finalSongs = buildFinalSongList(
                spotifySongs, filteredSourceSongs, dbById, dbSongIds,
                sourceIds, spotifyIds, previouslyExcluded, restoreRemoved);

        // 4. Calcular qué canciones se deben marcar como excluidas para no re-agregarlas en el futuro
        Set<String> excludedForSave = computeExcludedIds(
                previouslyExcluded, dbSongs, spotifyIds, sourceIds,
                finalSongs.keySet(), restoreRemoved);

        // 5. Clasificar género/idioma de canciones nuevas via ChatGPT, luego ordenar cronológicamente
        List<Song> finalList = new ArrayList<>(finalSongs.values());
        classificationService.classifySongs(finalList);
        finalList = sortByReleaseDate(finalList);

        // 6. Si no quedaron canciones, eliminar la playlist tanto de Spotify como de BD
        if (finalList.isEmpty()) {
            spotifyApi.unfollowPlaylist(authentication, splitifyPlaylist.getSpotifyId());
            deleteSplitifyPlaylistCascade(id);
            log.info("Playlist de Splitify '{}' eliminada (quedó vacía tras actualización)",
                    splitifyPlaylist.getName());
            return null;
        }

        // 7. Escribir el resultado: reemplazar canciones en Spotify y recrear registros en BD
        List<String> trackIds = finalList.stream().map(Song::getSpotifyId).toList();
        spotifyApi.replaceTracks(restClient, splitifyPlaylist.getSpotifyId(), trackIds);

        // Estrategia delete-and-recreate: borra todas las canciones de BD y las vuelve a insertar
        songRepository.deleteByPlaylistId(id);
        saveSongsForPlaylist(finalList, splitifyPlaylist, sourceIds);
        // También guardar las excluidas (con excluded=true) para recordar no re-agregarlas
        saveExcludedSongs(excludedForSave, filteredSourceSongs, dbSongs, splitifyPlaylist);

        splitifyPlaylist.setTotalTracks(finalList.size());
        playlistRepository.save(splitifyPlaylist);

        log.info("Playlist de Splitify '{}' actualizada: {} canciones",
                splitifyPlaylist.getName(), finalList.size());
        return toDto(splitifyPlaylist);
    }

    // Elimina una playlist Splitify: primero hace unfollow en Spotify (equivale a borrarla),
    // luego elimina sources, canciones y playlist de la BD en cascada.
    @Transactional
    public void deleteSplitifyPlaylist(OAuth2AuthenticationToken authentication, Long id) {
        String userId = authentication.getName();
        Playlist playlist = findSplitifyPlaylist(id, userId);
        spotifyApi.unfollowPlaylist(authentication, playlist.getSpotifyId());
        deleteSplitifyPlaylistCascade(playlist.getId());
    }

    // Eliminación masiva: mismo flujo que deleteSplitifyPlaylist pero para varias a la vez.
    @Transactional
    public void deleteSplitifyPlaylists(OAuth2AuthenticationToken authentication, List<Long> ids) {
        String userId = authentication.getName();
        for (Long id : ids) {
            playlistRepository.findById(id)
                    .filter(p -> p.getUserId().equals(userId) && p.isSplitify())
                    .ifPresent(p -> {
                        spotifyApi.unfollowPlaylist(authentication, p.getSpotifyId());
                        deleteSplitifyPlaylistCascade(p.getId());
                    });
        }
    }

    // Crea playlists organizadas automáticamente en Spotify según los criterios seleccionados.
    // Por cada criterio activo, agrupa las canciones y crea una playlist real en Spotify por grupo.
    // Ej: byLanguage crea "Splitify English Songs", "Splitify Español Songs", etc.
    public List<PlaylistDto> createOrganizedPlaylists(OAuth2AuthenticationToken authentication,
                                                       List<Long> playlistIds,
                                                       boolean byLanguage,
                                                       boolean byGenre,
                                                       boolean byReleaseDate) {
        String userId = authentication.getName();
        RestClient restClient = spotifyApi.createRestClient(authentication);
        List<Playlist> sourcePlaylists = findPlaylistsByIds(playlistIds);
        // Juntar canciones de todas las playlists seleccionadas, sin duplicados
        List<Song> allSongs = collectDeduplicatedSongs(playlistIds);
        List<PlaylistDto> createdPlaylists = new ArrayList<>();

        if (byLanguage) {
            for (var entry : groupByLanguage(allSongs).entrySet()) {
                String name = "Splitify " + entry.getKey() + " Songs";
                List<Song> sorted = sortByReleaseDate(entry.getValue());
                createdPlaylists.add(createSpotifyPlaylist(restClient, userId, name, sorted,
                        sourcePlaylists, "language", entry.getKey()));
            }
        }

        if (byGenre) {
            for (var entry : groupByGenre(allSongs).entrySet()) {
                String name = "Splitify " + entry.getKey() + " Songs";
                List<Song> sorted = sortByReleaseDate(entry.getValue());
                createdPlaylists.add(createSpotifyPlaylist(restClient, userId, name, sorted,
                        sourcePlaylists, "genre", entry.getKey()));
            }
        }

        if (byReleaseDate) {
            List<Song> sorted = sortByReleaseDate(new ArrayList<>(allSongs));
            createdPlaylists.add(createSpotifyPlaylist(restClient, userId,
                    "Splitify Songs By Release Date", sorted, sourcePlaylists, "releaseDate", null));
        }

        return createdPlaylists;
    }

    // Devuelve todos los idiomas, géneros y artistas disponibles en las playlists seleccionadas.
    // Se usa para poblar los dropdowns/filtros del modal de playlist personalizada.
    // TreeSet con Collator español para ordenar alfabéticamente respetando acentos (á=a, ñ después de n).
    public AvailableFiltersDto getAvailableFilters(List<Long> playlistIds) {
        Collator es = Collator.getInstance(new Locale("es"));
        es.setStrength(Collator.PRIMARY);
        Set<String> languages = new TreeSet<>(es);
        Set<String> genres = new TreeSet<>(es);
        Set<String> artists = new TreeSet<>(es);

        Set<String> seenSongIds = new HashSet<>();
        for (Long playlistId : playlistIds) {
            for (Song song : songRepository.findByPlaylistId(playlistId)) {
                if (song.isExcluded()) continue;
                if (!seenSongIds.add(song.getSpotifyId())) continue;

                addCommaSeparatedValues(song.getLanguage(), languages);
                if (song.getGenre() != null && !song.getGenre().isBlank()) {
                    genres.add(song.getGenre().trim());
                }
                addCommaSeparatedValues(song.getArtist(), artists);
            }
        }

        return new AvailableFiltersDto(
                new ArrayList<>(languages), new ArrayList<>(genres), new ArrayList<>(artists));
    }

    // Crea una playlist personalizada: el usuario elige nombre + filtros (idiomas, géneros, artistas).
    // Los filtros son inclusivos dentro de cada categoría (OR): si seleccionas "Pop" y "Rock", incluye ambos.
    // Pero entre categorías es AND: debe cumplir idioma Y género Y artista.
    public PlaylistDto createCustomPlaylist(OAuth2AuthenticationToken authentication,
                                             List<Long> playlistIds,
                                             List<String> languages,
                                             List<String> genres,
                                             List<String> artists,
                                             String playlistName) {
        String userId = authentication.getName();
        validateCustomPlaylistRequest(playlistIds, playlistName, languages, genres, artists);

        RestClient restClient = spotifyApi.createRestClient(authentication);
        List<Playlist> sourcePlaylists = findPlaylistsByIds(playlistIds);

        // Convertir listas a Sets para búsqueda O(1) en los filtros
        Set<String> languageSet = toFilterSet(languages);
        Set<String> genreSet = toFilterSet(genres);
        Set<String> artistSet = toFilterSet(artists);

        // Encadenar filtros: cada .filter() es AND entre categorías, pero dentro de cada
        // matchesX() es OR (ej: matchesLanguages pasa si la canción tiene CUALQUIER idioma seleccionado)
        List<Song> filtered = collectDeduplicatedSongs(playlistIds).stream()
                .filter(s -> matchesLanguages(s, languageSet))
                .filter(s -> matchesGenres(s, genreSet))
                .filter(s -> matchesArtists(s, artistSet))
                .collect(Collectors.toCollection(ArrayList::new));

        if (filtered.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se encontraron canciones que coincidan con los criterios seleccionados.");
        }

        List<Song> sorted = sortByReleaseDate(filtered);
        PlaylistDto created = createSpotifyPlaylist(restClient, userId, playlistName.trim(), sorted,
                sourcePlaylists, "custom", null);

        if (created == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo crear la playlist personalizada.");
        }
        return created;
    }

    // ──── Sync helpers ──────────────────────────────────────────────

    // Crea un mapa spotifyId → Playlist de todas las playlists no-Splitify del usuario en BD.
    // Se usa para comparar rápidamente qué ya existe vs. lo que viene de Spotify.
    private Map<String, Playlist> indexExistingPlaylists(String userId) {
        Map<String, Playlist> map = new LinkedHashMap<>();
        for (Playlist p : playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, false)) {
            map.put(p.getSpotifyId(), p);
        }
        return map;
    }

    private Set<String> getSplitifySpotifyIds(String userId) {
        return playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, true).stream()
                .map(Playlist::getSpotifyId)
                .collect(Collectors.toSet());
    }

    // Filtro de Spotify Dev Mode: solo podemos acceder a los tracks de playlists que el usuario
    // creó o donde es colaborador. Las playlists que solo sigue devuelven 403.
    private boolean isOwnedOrCollaborative(SpotifyDto.PlaylistItem item, String userId) {
        boolean isOwner = item.owner() != null && userId.equals(item.owner().id());
        return isOwner || item.collaborative();
    }

    // Spotify asigna un snapshotId único a cada versión de una playlist. Si no cambió,
    // no necesitamos re-descargar las canciones (ahorra llamadas a la API).
    private boolean isUnchanged(Playlist existing, SpotifyDto.PlaylistItem item) {
        return existing != null
                && existing.getSnapshotId() != null
                && existing.getSnapshotId().equals(item.snapshotId());
    }

    private void updatePlaylistMetadata(Playlist playlist, SpotifyDto.PlaylistItem item) {
        playlist.setName(item.name());
        playlist.setImageUrl(extractImageUrl(item.images()));
        playlist.setTotalTracks(item.tracks() != null ? item.tracks().total() : 0);
        playlistRepository.save(playlist);
    }

    // Crear o actualizar una playlist en BD. Si ya existía, borra sus canciones viejas primero
    // (se re-descargarán en syncPlaylistTracks). Si es nueva, crea el registro desde cero.
    private Playlist upsertPlaylist(Playlist existing, SpotifyDto.PlaylistItem item, String userId) {
        if (existing != null) {
            songRepository.deleteByPlaylistId(existing.getId());
            existing.setName(item.name());
            existing.setImageUrl(extractImageUrl(item.images()));
            existing.setTotalTracks(item.tracks() != null ? item.tracks().total() : 0);
            existing.setSnapshotId(item.snapshotId());
            return playlistRepository.save(existing);
        }

        Playlist playlist = Playlist.builder()
                .spotifyId(item.id())
                .name(item.name())
                .imageUrl(extractImageUrl(item.images()))
                .totalTracks(item.tracks() != null ? item.tracks().total() : 0)
                .snapshotId(item.snapshotId())
                .userId(userId)
                .build();
        return playlistRepository.save(playlist);
    }

    // Liked Songs es especial: no es una playlist real de Spotify, así que se maneja con un endpoint
    // diferente y un ID ficticio "liked_songs". Se trae, guarda, y clasifica igual que las demás.
    private Playlist syncLikedSongs(RestClient restClient, String userId, Playlist existing) {
        SpotifyDto.LikedTracksResponse response = spotifyApi.fetchLikedSongs(restClient, 10);
        int total = response != null ? response.total() : 0;

        Playlist likedSongs;
        if (existing != null) {
            songRepository.deleteByPlaylistId(existing.getId());
            existing.setTotalTracks(total);
            likedSongs = playlistRepository.save(existing);
        } else {
            likedSongs = playlistRepository.save(Playlist.builder()
                    .spotifyId("liked_songs")
                    .name("Liked Songs")
                    .totalTracks(total)
                    .userId(userId)
                    .build());
        }

        if (response != null && response.items() != null) {
            List<Song> savedSongs = saveTracks(response.items(), likedSongs);
            classificationService.classifySongs(savedSongs);
        }
        return likedSongs;
    }

    // Descarga las canciones de una playlist desde Spotify, las guarda en BD, y las envía a
    // ClassificationService para obtener género/idioma via ChatGPT. El catch evita que una
    // playlist con error (ej: 403 por permisos) detenga el sync de las demás.
    private void syncPlaylistTracks(RestClient restClient, Playlist playlist) {
        try {
            SpotifyDto.TracksResponse response =
                    spotifyApi.fetchPlaylistTracks(restClient, playlist.getSpotifyId(), 10);
            if (response != null && response.items() != null && !response.items().isEmpty()) {
                List<Song> savedSongs = saveTracks(response.items(), playlist);
                classificationService.classifySongs(savedSongs);
            }
        } catch (Exception e) {
            log.warn("Could not fetch items for '{}': {}", playlist.getName(), e.getMessage());
        }
    }

    // Limpieza: playlists que teníamos en BD pero no aparecieron en el sync actual de Spotify.
    // Significa que el usuario las borró o dejó de ser owner/colaborador → eliminar de BD.
    private void removeDeletedPlaylists(Map<String, Playlist> existingBySpotifyId,
                                         Set<String> seenSpotifyIds) {
        for (Playlist old : existingBySpotifyId.values()) {
            if (!seenSpotifyIds.contains(old.getSpotifyId())) {
                songRepository.deleteByPlaylistId(old.getId());
                playlistRepository.delete(old);
            }
        }
    }

    // Playlists Splitify huérfanas: las creó Splitify pero el usuario las borró directamente
    // desde Spotify. Como ya no existen allá, limpiamos su registro de la BD.
    private void removeOrphanedSplitifyPlaylists(String userId, Set<String> allSpotifyIds) {
        for (Playlist sp : playlistRepository.findByUserIdAndSplitifyOrderByIdAsc(userId, true)) {
            if (!allSpotifyIds.contains(sp.getSpotifyId())) {
                log.info("Playlist de Splitify '{}' ya no existe en Spotify, eliminando de BD",
                        sp.getName());
                deleteSplitifyPlaylistCascade(sp.getId());
            }
        }
    }

    // ──── Song mapping ──────────────────────────────────────────────

    // Convierte un TrackWrapper de la API de Spotify a nuestra entidad Song.
    // Extrae: ID, nombre, artistas (concatenados con coma), y fecha de lanzamiento del álbum.
    // Género e idioma quedan null aquí — los llena ClassificationService después.
    private Song trackToSong(SpotifyDto.TrackWrapper wrapper) {
        SpotifyDto.Track track = wrapper.track();
        if (track == null) return null;

        String artistName = track.artists() != null && !track.artists().isEmpty()
                ? track.artists().stream()
                    .map(SpotifyDto.Artist::name)
                    .collect(Collectors.joining(", "))
                : "Desconocido";
        String spotifyDate = track.album() != null ? track.album().releaseDate() : null;

        return Song.builder()
                .spotifyId(track.id())
                .name(track.name())
                .artist(artistName)
                .releaseYear(extractYear(spotifyDate))
                .releaseDate(spotifyDate)
                .build();
    }

    // Trae TODAS las canciones de una playlist de Spotify (paginando internamente en SpotifyApiClient).
    // Se usa en refresh para comparar el estado actual de Spotify con lo que tenemos en BD.
    private List<Song> fetchSongsFromSpotify(RestClient restClient, String playlistId) {
        return spotifyApi.fetchAllPlaylistTracks(restClient, playlistId).stream()
                .map(this::trackToSong)
                .filter(Objects::nonNull)
                .toList();
    }

    // Convierte TrackWrappers a Songs, los asocia a la playlist, y los guarda en BD.
    // Retorna la lista guardada para que luego se le pase a ClassificationService.
    private List<Song> saveTracks(List<SpotifyDto.TrackWrapper> items, Playlist playlist) {
        List<Song> savedSongs = new ArrayList<>();
        for (SpotifyDto.TrackWrapper wrapper : items) {
            Song song = trackToSong(wrapper);
            if (song == null) continue;
            song.setPlaylist(playlist);
            savedSongs.add(songRepository.save(song));
        }
        return savedSongs;
    }

    // ──── DB helpers ────────────────────────────────────────────────

    // Eliminación en cascada manual: borra en orden sources → canciones → playlist
    // para respetar las foreign keys de JPA (no hay cascade automático configurado).
    private void deleteSplitifyPlaylistCascade(Long playlistId) {
        sourceRepository.deleteBySplitifyPlaylistId(playlistId);
        songRepository.deleteByPlaylistId(playlistId);
        playlistRepository.deleteById(playlistId);
    }

    // Busca una playlist Splitify verificando que pertenezca al usuario actual (seguridad).
    private Playlist findSplitifyPlaylist(Long id, String userId) {
        return playlistRepository.findById(id)
                .filter(p -> p.getUserId().equals(userId) && p.isSplitify())
                .orElseThrow(() -> new RuntimeException("Playlist not found"));
    }

    private List<Playlist> findPlaylistsByIds(List<Long> ids) {
        return ids.stream()
                .map(id -> playlistRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    // Guarda las canciones finales de una playlist Splitify. Marca como manuallyAdded=true
    // las canciones que el usuario agregó directamente en Spotify (no vienen de las fuentes).
    private void saveSongsForPlaylist(List<Song> songs, Playlist playlist, Set<String> sourceIds) {
        for (Song s : songs) {
            songRepository.save(Song.builder()
                    .spotifyId(s.getSpotifyId())
                    .name(s.getName())
                    .artist(s.getArtist())
                    .releaseYear(s.getReleaseYear())
                    .releaseDate(s.getReleaseDate())
                    .genre(s.getGenre())
                    .language(s.getLanguage())
                    .playlist(playlist)
                    .excluded(false)
                    .manuallyAdded(!sourceIds.contains(s.getSpotifyId()))
                    .build());
        }
    }


    //todo: entender bien
    // Guarda canciones excluidas (excluded=true) en BD. Son canciones que el usuario quitó
    // manualmente de la playlist en Spotify. Se guardan para "recordar" no re-agregarlas
    // en futuros refreshes. Busca los datos de la canción primero en las fuentes, luego en BD.
    private void saveExcludedSongs(Set<String> excludedIds, List<Song> sourceSongs,
                                    List<Song> dbSongs, Playlist playlist) {
        Map<String, Song> sourceById = new LinkedHashMap<>();
        for (Song s : sourceSongs) { sourceById.put(s.getSpotifyId(), s); }
        for (Song s : dbSongs) { sourceById.putIfAbsent(s.getSpotifyId(), s); }

        for (String exId : excludedIds) {
            Song src = sourceById.get(exId);
            if (src != null) {
                songRepository.save(Song.builder()
                        .spotifyId(src.getSpotifyId())
                        .name(src.getName())
                        .artist(src.getArtist())
                        .releaseYear(src.getReleaseYear())
                        .releaseDate(src.getReleaseDate())
                        .genre(src.getGenre())
                        .language(src.getLanguage())
                        .playlist(playlist)
                        .excluded(true)
                        .build());
            }
        }
    }

    // ──── Spotify playlist creation ─────────────────────────────────

    // Orquesta la creación completa de una playlist Splitify:
    //   1. Crea la playlist vacía en Spotify (POST /me/playlists)
    //   2. Agrega las canciones a la playlist en Spotify (POST /playlists/{id}/items)
    //   3. Guarda la playlist en BD con splitify=true y el tipo de filtro usado
    //   4. Registra qué playlists origen la generaron (SplitifyPlaylistSource)
    //   5. Guarda las canciones en BD asociadas a esta nueva playlist
    private PlaylistDto createSpotifyPlaylist(RestClient restClient, String userId, String name,
                                               List<Song> songs, List<Playlist> sourcePlaylists,
                                               String filterType, String filterValue) {
        if (songs.isEmpty()) return null;

        SpotifyDto.CreatePlaylistResponse created = spotifyApi.createPlaylist(restClient, name);
        List<String> trackIds = songs.stream().map(Song::getSpotifyId).toList();
        spotifyApi.addTracks(restClient, created.id(), trackIds);

        Playlist playlist = playlistRepository.save(Playlist.builder()
                .spotifyId(created.id())
                .name(name)
                .totalTracks(songs.size())
                .userId(userId)
                .splitify(true)
                .filterType(filterType)
                .filterValue(filterValue)
                .build());

        for (Playlist source : sourcePlaylists) {
            sourceRepository.save(SplitifyPlaylistSource.builder()
                    .splitifyPlaylist(playlist)
                    .sourcePlaylist(source)
                    .build());
        }

        for (Song s : songs) {
            songRepository.save(Song.builder()
                    .spotifyId(s.getSpotifyId())
                    .name(s.getName())
                    .artist(s.getArtist())
                    .releaseYear(s.getReleaseYear())
                    .releaseDate(s.getReleaseDate())
                    .genre(s.getGenre())
                    .language(s.getLanguage())
                    .playlist(playlist)
                    .build());
        }

        log.info("Playlist creada en Spotify: '{}' con {} canciones", name, songs.size());
        return toDto(playlist);
    }

    // ──── Filtering ─────────────────────────────────────────────────

    // Junta canciones de varias playlists eliminando duplicados por spotifyId.
    // Si una misma canción aparece en 2 playlists, solo se incluye una vez.
    // Ignora canciones marcadas como excluidas.
    private List<Song> collectDeduplicatedSongs(List<Long> playlistIds) {
        List<Song> songs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Long playlistId : playlistIds) {
            for (Song song : songRepository.findByPlaylistId(playlistId)) {
                if (!song.isExcluded() && seen.add(song.getSpotifyId())) {
                    songs.add(song);
                }
            }
        }
        return songs;
    }

    // Obtiene las canciones actuales de las playlists origen de una playlist Splitify,
    // y les aplica el mismo filtro con el que se creó originalmente (ej: language=English).
    // Si una playlist origen fue eliminada de BD, limpia su registro de source.
    private List<Song> getFilteredSourceSongs(Long splitifyPlaylistId, Playlist splitifyPlaylist) {
        List<SplitifyPlaylistSource> sources =
                sourceRepository.findBySplitifyPlaylistId(splitifyPlaylistId);
        List<Song> sourceSongs = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (SplitifyPlaylistSource source : sources) {
            if (!playlistRepository.existsById(source.getSourcePlaylist().getId())) {
                sourceRepository.delete(source);
                continue;
            }
            for (Song song : songRepository.findByPlaylistId(source.getSourcePlaylist().getId())) {
                if (seenIds.add(song.getSpotifyId())) {
                    sourceSongs.add(song);
                }
            }
        }

        return applyFilter(sourceSongs, splitifyPlaylist.getFilterType(),
                splitifyPlaylist.getFilterValue());
    }

    // Aplica el filtro original de la playlist Splitify. filterType puede ser:
    //   - "language": solo canciones de ese idioma
    //   - "genre": solo canciones de ese género
    //   - "releaseDate" o null: sin filtro, devuelve todas (se ordenan después)
    //   - "custom": no pasa por aquí (las custom se filtran en createCustomPlaylist)
    private List<Song> applyFilter(List<Song> songs, String filterType, String filterValue) {
        if (filterType == null || "releaseDate".equals(filterType)) {
            return new ArrayList<>(songs);
        }
        return songs.stream().filter(song -> {
            if ("language".equals(filterType)) {
                return matchesLanguages(song, Set.of(filterValue));
            } else if ("genre".equals(filterType)) {
                return filterValue.equals(song.getGenre());
            }
            return true;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    // Matching inclusivo: una canción puede tener varios idiomas separados por coma (ej: "English, Español").
    // Devuelve true si CUALQUIERA de los idiomas de la canción está en el set de filtros.
    private boolean matchesLanguages(Song song, Set<String> wanted) {
        if (wanted.isEmpty()) return true;
        if (song.getLanguage() == null || song.getLanguage().isBlank()) return false;
        for (String lang : song.getLanguage().split(",\\s*")) {
            if (wanted.contains(lang.trim())) return true;
        }
        return false;
    }

    private boolean matchesGenres(Song song, Set<String> wanted) {
        if (wanted.isEmpty()) return true;
        return song.getGenre() != null && wanted.contains(song.getGenre().trim());
    }

    private boolean matchesArtists(Song song, Set<String> wanted) {
        if (wanted.isEmpty()) return true;
        if (song.getArtist() == null || song.getArtist().isBlank()) return false;
        for (String artist : song.getArtist().split(",\\s*")) {
            if (wanted.contains(artist.trim())) return true;
        }
        return false;
    }

    // ──── Refresh helpers ───────────────────────────────────────────

    // Métod0 central del refresh: decide qué canciones quedan en la playlist Splitify.
    // Reconcilia 3 fuentes: Spotify (estado real), BD (último sync), y fuentes (playlists origen).
    //
    // La lógica en 3 pasadas:
    //   Pasada 1 - Canciones que están en Spotify ahora:
    //     - Si ya no está en las fuentes Y no fue agregada manualmente → sacarla (la fuente la quitó)
    //     - Si sigue vigente → mantenerla, reutilizando género/idioma ya clasificados de la BD
    //   Pasada 2 - Canciones nuevas de las fuentes:
    //     - Si no estaba en Spotify NI en BD → es nueva, agregarla
    //   Pasada 3 - Restaurar eliminadas (solo si restoreRemoved=true):
    //     - Canciones que el usuario quitó de Spotify pero siguen en las fuentes → re-agregar
    //     - Respeta las previouslyExcluded (no restaura las que ya estaban excluidas antes)
    private Map<String, Song> buildFinalSongList(
            List<Song> spotifySongs, List<Song> filteredSourceSongs,
            Map<String, Song> dbById, Set<String> dbSongIds, Set<String> sourceIds,
            Set<String> spotifyIds, Set<String> previouslyExcluded, boolean restoreRemoved) {

        Map<String, Song> finalSongs = new LinkedHashMap<>();

        // Pasada 1: procesar canciones que están actualmente en Spotify
        for (Song s : spotifySongs) {
            boolean stillInSources = sourceIds.contains(s.getSpotifyId());
            Song dbVersion = dbById.get(s.getSpotifyId());

            // Si la canción ya no está en las fuentes, la conocíamos en BD, y no fue agregada
            // manualmente por el usuario → descartarla (la playlist origen la quitó)
            if (!stillInSources && dbVersion != null
                    && !dbVersion.isManuallyAdded() && !dbVersion.isExcluded()) {
                continue;
            }

            // Reutilizar género/idioma ya clasificados para evitar re-llamar a ChatGPT
            if (dbVersion != null && !dbVersion.isExcluded()) {
                s.setGenre(dbVersion.getGenre());
                s.setLanguage(dbVersion.getLanguage());
            }
            finalSongs.put(s.getSpotifyId(), s);
        }

        // Pasada 2: canciones nuevas de las fuentes que no estaban ni en Spotify ni en BD
        for (Song s : filteredSourceSongs) {
            if (!finalSongs.containsKey(s.getSpotifyId())
                    && !dbSongIds.contains(s.getSpotifyId())) {
                finalSongs.put(s.getSpotifyId(), s);
            }
        }

        // Pasada 3: si el usuario pidió restaurar, re-agregar canciones que quitó manualmente
        // de Spotify pero que siguen disponibles en las fuentes
        if (restoreRemoved) {
            for (Song s : filteredSourceSongs) {
                if (!finalSongs.containsKey(s.getSpotifyId())
                        && dbSongIds.contains(s.getSpotifyId())
                        && !previouslyExcluded.contains(s.getSpotifyId())
                        && !spotifyIds.contains(s.getSpotifyId())) {
                    finalSongs.put(s.getSpotifyId(), s);
                }
            }
        }

        return finalSongs;
    }

    // Calcula qué canciones deben guardarse como excluidas (excluded=true) en BD.
    // Esto le dice a futuros refreshes "no re-agregar estas canciones, el usuario las quitó".
    //   - Empieza con las que ya estaban excluidas antes
    //   - Si no se pidió restaurar: agrega las que estaban en BD pero ya no están en Spotify
    //     (el usuario las quitó manualmente) y siguen en las fuentes
    //   - Limpia: quita las que terminaron en la lista final y las que ya no están en las fuentes
    private Set<String> computeExcludedIds(Set<String> previouslyExcluded, List<Song> dbSongs,
                                            Set<String> spotifyIds, Set<String> sourceIds,
                                            Set<String> finalSongIds, boolean restoreRemoved) {
        Set<String> excluded = new HashSet<>(previouslyExcluded);

        if (!restoreRemoved) {
            // Detectar nuevas exclusiones: estaban en BD, el usuario las quitó de Spotify,
            // pero siguen en las fuentes → marcarlas como excluidas
            for (Song s : dbSongs) {
                if (!s.isExcluded() && !spotifyIds.contains(s.getSpotifyId())
                        && sourceIds.contains(s.getSpotifyId())) {
                    excluded.add(s.getSpotifyId());
                }
            }
        }

        // No excluir canciones que terminaron en la lista final (contradicción)
        excluded.removeAll(finalSongIds);
        // Solo excluir canciones que siguen existiendo en las fuentes (las demás son irrelevantes)
        excluded.retainAll(sourceIds);
        return excluded;
    }

    // ──── Grouping ──────────────────────────────────────────────────

    // Agrupa canciones por idioma. Una canción multilingüe (ej: "English, Español") aparece
    // en ambos grupos. Canciones sin idioma clasificado se ignoran.
    private Map<String, List<Song>> groupByLanguage(List<Song> songs) {
        Map<String, List<Song>> byLang = new LinkedHashMap<>();
        for (Song song : songs) {
            if (song.getLanguage() == null || song.getLanguage().isBlank()) continue;
            for (String lang : song.getLanguage().split(",\\s*")) {
                byLang.computeIfAbsent(lang.trim(), k -> new ArrayList<>()).add(song);
            }
        }
        return byLang;
    }

    private Map<String, List<Song>> groupByGenre(List<Song> songs) {
        return songs.stream()
                .filter(s -> s.getGenre() != null && !s.getGenre().isBlank())
                .collect(Collectors.groupingBy(Song::getGenre, LinkedHashMap::new, Collectors.toList()));
    }

    // ──── Validation ────────────────────────────────────────────────

    private void validateCustomPlaylistRequest(List<Long> playlistIds, String playlistName,
                                                List<String> languages, List<String> genres,
                                                List<String> artists) {
        if (playlistIds == null || playlistIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes seleccionar al menos una playlist origen.");
        }
        if (playlistName == null || playlistName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes escribir un nombre para la playlist.");
        }
        if (toFilterSet(languages).isEmpty() && toFilterSet(genres).isEmpty()
                && toFilterSet(artists).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes seleccionar al menos un filtro (idioma, género o artista).");
        }
    }

    // ──── Utilities ─────────────────────────────────────────────────

    // Toma la primera imagen de la lista (Spotify devuelve varias resoluciones, la primera es la más grande).
    private String extractImageUrl(List<SpotifyDto.Image> images) {
        return images != null && !images.isEmpty() ? images.getFirst().url() : null;
    }

    // Spotify devuelve fechas como "2024-03-15" o solo "2024". Extrae los primeros 4 caracteres como año.
    private Integer extractYear(String spotifyDate) {
        if (spotifyDate == null || spotifyDate.isBlank()) return null;
        try {
            return Integer.parseInt(spotifyDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Ordena cronológicamente (más antigua primero). Canciones sin fecha van al final.
    private List<Song> sortByReleaseDate(List<Song> songs) {
        songs.sort(Comparator.comparing(
                Song::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return songs;
    }

    private Set<String> toSpotifyIdSet(List<Song> songs) {
        return songs.stream().map(Song::getSpotifyId).collect(Collectors.toSet());
    }

    private Map<String, Song> indexBySpotifyId(List<Song> songs) {
        Map<String, Song> map = new LinkedHashMap<>();
        for (Song s : songs) { map.put(s.getSpotifyId(), s); }
        return map;
    }

    private Set<String> getExcludedIds(List<Song> songs) {
        return songs.stream()
                .filter(Song::isExcluded)
                .map(Song::getSpotifyId)
                .collect(Collectors.toSet());
    }

    // Convierte una lista de filtros del frontend a un Set limpio para búsqueda O(1).
    // Quita nulls, blanks, y espacios. Si la lista es null, devuelve Set vacío (sin filtro).
    private Set<String> toFilterSet(List<String> values) {
        if (values == null) return Set.of();
        return values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    // Parsea campos multi-valor separados por coma (ej: "Bad Bunny, Shakira") y agrega cada uno al Set.
    private void addCommaSeparatedValues(String field, Set<String> target) {
        if (field == null || field.isBlank()) return;
        for (String value : field.split(",\\s*")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) target.add(trimmed);
        }
    }

    // Conversores entidad → DTO para devolver al frontend (sin exponer campos internos de JPA).
    private PlaylistDto toDto(Playlist playlist) {
        return new PlaylistDto(
                playlist.getId(), playlist.getSpotifyId(), playlist.getName(),
                playlist.getImageUrl(), playlist.getTotalTracks());
    }

    private SongDto toSongDto(Song s) {
        return new SongDto(
                s.getId(), s.getSpotifyId(), s.getName(), s.getArtist(),
                s.getReleaseYear(), s.getGenre(), s.getLanguage());
    }
}
