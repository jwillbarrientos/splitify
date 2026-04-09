package io.jona.smusic.sorted_music.service;

import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.SpotifyDto;
import io.jona.smusic.sorted_music.dto.UserProfileDto;
import io.jona.smusic.sorted_music.model.Playlist;
import io.jona.smusic.sorted_music.model.Song;
import io.jona.smusic.sorted_music.repository.PlaylistRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final ClassificationService classificationService;

    public UserProfileDto getUserProfile(OAuth2AuthenticationToken authentication) {
        String accessToken = getAccessToken(authentication);

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.spotify.com/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        SpotifyDto.UserProfileResponse profile = restClient.get()
                .uri("/me")
                .retrieve()
                .body(SpotifyDto.UserProfileResponse.class);

        String imageUrl = (profile != null && profile.images() != null && !profile.images().isEmpty())
                ? profile.images().getFirst().url()
                : null;

        return new UserProfileDto(
                profile != null ? profile.displayName() : null,
                imageUrl
        );
    }

    public List<PlaylistDto> syncPlaylists(OAuth2AuthenticationToken authentication) {
        String accessToken = getAccessToken(authentication);
        String userId = authentication.getName();

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.spotify.com/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        // Cachear clasificaciones existentes antes de borrar (evita re-clasificar con ChatGPT)
        List<Playlist> oldPlaylists = playlistRepository.findByUserIdAndSplitify(userId, false);
        Map<String, Song> classificationCache = new HashMap<>();
        for (Playlist old : oldPlaylists) {
            for (Song song : songRepository.findByPlaylistId(old.getId())) {
                if (song.getGenre() != null) {
                    classificationCache.put(song.getSpotifyId(), song);
                }
            }
        }

        // Borrar datos viejos del usuario (solo playlists sincronizadas, no las de Splitify)
        for (Playlist old : oldPlaylists) {
            songRepository.deleteByPlaylistId(old.getId());
        }
        playlistRepository.deleteAll(oldPlaylists);

        // Obtener los spotifyIds de playlists de Splitify para excluirlas del sync
        List<String> splitifySpotifyIds = playlistRepository.findByUserIdAndSplitify(userId, true).stream()
                .map(Playlist::getSpotifyId)
                .toList();

        List<Playlist> savedPlaylists = new ArrayList<>();

        // 1. Traer Liked Songs
        savedPlaylists.add(syncLikedSongs(restClient, userId, classificationCache));

        // 2. Traer todas las playlists del usuario
        String url = "/me/playlists?limit=50";
        while (url != null) {
            SpotifyDto.PlaylistsResponse response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(SpotifyDto.PlaylistsResponse.class);

            if (response == null || response.items() == null) break;

            for (SpotifyDto.PlaylistItem item : response.items()) {
                boolean isOwner = item.owner() != null && userId.equals(item.owner().id());
                if (!isOwner && !item.collaborative()) {
                    continue;
                }

                // Saltar playlists que fueron creadas por Splitify
                if (splitifySpotifyIds.contains(item.id())) {
                    continue;
                }

                Playlist playlist = Playlist.builder()
                        .spotifyId(item.id())
                        .name(item.name())
                        .imageUrl(item.images() != null && !item.images().isEmpty()
                                ? item.images().getFirst().url()
                                : null)
                        .totalTracks(item.tracks() != null ? item.tracks().total() : 0)
                        .userId(userId)
                        .build();
                playlist = playlistRepository.save(playlist);

                try {
                    syncPlaylistTracks(restClient, playlist, classificationCache);
                } catch (Exception e) {
                    log.warn("Could not fetch items for '{}': {}", item.name(), e.getMessage());
                }
                savedPlaylists.add(playlist);
            }

            url = response.next();
        }

        return savedPlaylists.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteUserPlaylists(String userId) {
        List<Playlist> playlists = playlistRepository.findByUserIdAndSplitify(userId, false);
        for (Playlist p : playlists) {
            songRepository.deleteByPlaylistId(p.getId());
        }
        playlistRepository.deleteAll(playlists);
    }

    public List<PlaylistDto> getUserPlaylists(String userId) {
        return playlistRepository.findByUserIdAndSplitify(userId, false).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<PlaylistDto> getSplitifyPlaylists(String userId) {
        return playlistRepository.findByUserIdAndSplitify(userId, true).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSplitifyPlaylist(OAuth2AuthenticationToken authentication, Long id) {
        String userId = authentication.getName();
        Playlist playlist = playlistRepository.findById(id)
                .filter(p -> p.getUserId().equals(userId) && p.isSplitify())
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        // Eliminar de Spotify (DELETE /me/library con URI de la playlist)
        unfollowPlaylistOnSpotify(authentication, playlist.getSpotifyId());

        songRepository.deleteByPlaylistId(playlist.getId());
        playlistRepository.deleteById(playlist.getId());
    }

    @Transactional
    public void deleteSplitifyPlaylists(OAuth2AuthenticationToken authentication, List<Long> ids) {
        String userId = authentication.getName();
        for (Long id : ids) {
            playlistRepository.findById(id)
                    .filter(p -> p.getUserId().equals(userId) && p.isSplitify())
                    .ifPresent(p -> {
                        unfollowPlaylistOnSpotify(authentication, p.getSpotifyId());
                        songRepository.deleteByPlaylistId(p.getId());
                        playlistRepository.deleteById(p.getId());
                    });
        }
    }

    private void unfollowPlaylistOnSpotify(OAuth2AuthenticationToken authentication, String spotifyPlaylistId) {
        String accessToken = getAccessToken(authentication);

        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.spotify.com/v1/playlists/" + spotifyPlaylistId + "/followers"))
                    .header("Authorization", "Bearer " + accessToken)
                    .DELETE()
                    .build();
            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("Unfollow playlist '{}' response: {} {}", spotifyPlaylistId, response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("Could not unfollow playlist '{}' on Spotify: {}", spotifyPlaylistId, e.getMessage());
        }
    }

    private Playlist syncLikedSongs(RestClient restClient, String userId, Map<String, Song> classificationCache) {
        SpotifyDto.LikedTracksResponse response = restClient.get()
                .uri("/me/tracks?limit=10")
                .retrieve()
                .body(SpotifyDto.LikedTracksResponse.class);

        int total = response != null ? response.total() : 0;

        Playlist likedSongs = Playlist.builder()
                .spotifyId("liked_songs")
                .name("Liked Songs")
                .imageUrl(null)
                .totalTracks(total)
                .userId(userId)
                .build();
        likedSongs = playlistRepository.save(likedSongs);

        if (response != null && response.items() != null) {
            List<Song> savedSongs = saveTracks(response.items(), likedSongs);
            applyClassificationCache(savedSongs, classificationCache);
            classificationService.classifySongs(savedSongs);
        }

        return likedSongs;
    }

    private void syncPlaylistTracks(RestClient restClient, Playlist playlist, Map<String, Song> classificationCache) {
        SpotifyDto.TracksResponse response = restClient.get()
                .uri("/playlists/{id}/items?limit=10", playlist.getSpotifyId())
                .retrieve()
                .body(SpotifyDto.TracksResponse.class);

        if (response != null && response.items() != null && !response.items().isEmpty()) {
            List<Song> savedSongs = saveTracks(response.items(), playlist);
            applyClassificationCache(savedSongs, classificationCache);
            classificationService.classifySongs(savedSongs);
        }
    }

    private void applyClassificationCache(List<Song> songs, Map<String, Song> cache) {
        for (Song song : songs) {
            Song cached = cache.get(song.getSpotifyId());
            if (cached != null) {
                song.setReleaseYear(cached.getReleaseYear());
                song.setReleaseDate(cached.getReleaseDate());
                song.setGenre(cached.getGenre());
                song.setLanguage(cached.getLanguage());
                songRepository.save(song);
            }
        }
    }

    private List<Song> saveTracks(List<SpotifyDto.TrackWrapper> items, Playlist playlist) {
        List<Song> savedSongs = new ArrayList<>();
        for (SpotifyDto.TrackWrapper wrapper : items) {
            if (wrapper.track() == null) continue;

            SpotifyDto.Track track = wrapper.track();
            String artistName = track.artists() != null && !track.artists().isEmpty()
                    ? track.artists().stream()
                        .map(SpotifyDto.Artist::name)
                        .collect(Collectors.joining(", "))
                    : "Desconocido";

            String spotifyDate = track.album() != null ? track.album().releaseDate() : null;
            Integer releaseYear = extractYear(spotifyDate);

            Song song = Song.builder()
                    .spotifyId(track.id())
                    .name(track.name())
                    .artist(artistName)
                    .releaseYear(releaseYear)
                    .releaseDate(spotifyDate)
                    .playlist(playlist)
                    .build();
            savedSongs.add(songRepository.save(song));
        }
        return savedSongs;
    }

    public List<PlaylistDto> createOrganizedPlaylists(OAuth2AuthenticationToken authentication,
                                                       List<Long> playlistIds,
                                                       boolean byLanguage,
                                                       boolean byGenre,
                                                       boolean byReleaseDate) {
        String accessToken = getAccessToken(authentication);
        String userId = authentication.getName();

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.spotify.com/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        // Obtener todas las canciones de las playlists seleccionadas, deduplicar por spotifyId
        List<Song> allSongs = new ArrayList<>();
        Set<String> seenSpotifyIds = new HashSet<>();
        for (Long playlistId : playlistIds) {
            for (Song song : songRepository.findByPlaylistId(playlistId)) {
                if (seenSpotifyIds.add(song.getSpotifyId())) {
                    allSongs.add(song);
                }
            }
        }

        List<PlaylistDto> createdPlaylists = new ArrayList<>();

        if (byLanguage) {
            // Agrupar por idioma (una canción con "Inglés, Español" aparece en ambos grupos)
            Map<String, List<Song>> byLang = new LinkedHashMap<>();
            for (Song song : allSongs) {
                if (song.getLanguage() == null || song.getLanguage().isBlank()) continue;
                for (String lang : song.getLanguage().split(",\\s*")) {
                    byLang.computeIfAbsent(lang.trim(), k -> new ArrayList<>()).add(song);
                }
            }
            for (Map.Entry<String, List<Song>> entry : byLang.entrySet()) {
                String name = "Splitify " + entry.getKey() + " Songs";
                List<Song> sorted = sortByReleaseDate(entry.getValue());
                createdPlaylists.add(createSpotifyPlaylist(restClient, userId, name, sorted));
            }
        }

        if (byGenre) {
            Map<String, List<Song>> byGen = allSongs.stream()
                    .filter(s -> s.getGenre() != null && !s.getGenre().isBlank())
                    .collect(Collectors.groupingBy(Song::getGenre, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<Song>> entry : byGen.entrySet()) {
                String name = "Splitify " + entry.getKey() + " Songs";
                List<Song> sorted = sortByReleaseDate(entry.getValue());
                createdPlaylists.add(createSpotifyPlaylist(restClient, userId, name, sorted));
            }
        }

        if (byReleaseDate) {
            String name = "Splitify Songs By Release Date";
            List<Song> sorted = sortByReleaseDate(new ArrayList<>(allSongs));
            createdPlaylists.add(createSpotifyPlaylist(restClient, userId, name, sorted));
        }

        return createdPlaylists;
    }

    private List<Song> sortByReleaseDate(List<Song> songs) {
        songs.sort(Comparator.comparing(
                Song::getReleaseDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));
        return songs;
    }

    private PlaylistDto createSpotifyPlaylist(RestClient restClient, String userId, String name, List<Song> songs) {
        if (songs.isEmpty()) return null;

        String safeName = name.replace("\"", "'");
        String createBody = "{\"name\":\"" + safeName + "\",\"public\":false,\"description\":\"Playlist creada por Splitify\"}";

        SpotifyDto.CreatePlaylistResponse created = restClient.post()
                .uri("/me/playlists")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .body(SpotifyDto.CreatePlaylistResponse.class);

        // Agregar canciones en lotes de 100 (límite de Spotify)
        List<String> uris = songs.stream()
                .map(s -> "spotify:track:" + s.getSpotifyId())
                .toList();

        for (int i = 0; i < uris.size(); i += 100) {
            List<String> batch = uris.subList(i, Math.min(i + 100, uris.size()));
            String addBody = "{\"uris\":" + batch.stream()
                    .map(u -> "\"" + u + "\"")
                    .collect(Collectors.joining(",", "[", "]")) + "}";

            restClient.post()
                    .uri("/playlists/{playlistId}/items", created.id())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(addBody)
                    .retrieve()
                    .toBodilessEntity();
        }

        // Guardar en BD como playlist de Splitify
        Playlist playlist = Playlist.builder()
                .spotifyId(created.id())
                .name(name)
                .totalTracks(songs.size())
                .userId(userId)
                .splitify(true)
                .build();
        playlist = playlistRepository.save(playlist);

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

    private Integer extractYear(String spotifyDate) {
        if (spotifyDate == null || spotifyDate.isBlank()) return null;
        try {
            return Integer.parseInt(spotifyDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getAccessToken(OAuth2AuthenticationToken authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName()
        );
        if (client == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Sesión de Spotify expirada");
        }
        return client.getAccessToken().getTokenValue();
    }

    private PlaylistDto toDto(Playlist playlist) {
        return new PlaylistDto(
                playlist.getId(),
                playlist.getSpotifyId(),
                playlist.getName(),
                playlist.getImageUrl(),
                playlist.getTotalTracks()
        );
    }
}
