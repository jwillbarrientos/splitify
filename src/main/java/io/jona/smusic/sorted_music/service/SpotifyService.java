package io.jona.smusic.sorted_music.service;

import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.SpotifyDto;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotifyService {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;

    public List<PlaylistDto> syncPlaylists(OAuth2AuthenticationToken authentication) {
        String accessToken = getAccessToken(authentication);
        String userId = authentication.getName();

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.spotify.com/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        // Borrar datos viejos del usuario (solo playlists sincronizadas, no las de Splitify)
        List<Playlist> oldPlaylists = playlistRepository.findByUserIdAndSplitify(userId, false);
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
        savedPlaylists.add(syncLikedSongs(restClient, userId));

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
                    syncPlaylistTracks(restClient, playlist);
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

    public PlaylistDto createTestPlaylist(OAuth2AuthenticationToken authentication, List<Long> playlistIds) {
        String accessToken = getAccessToken(authentication);
        String userId = authentication.getName();

        RestClient restClient = RestClient.builder()
                .baseUrl("https://api.spotify.com/v1")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        // Obtener canciones de las playlists seleccionadas y elegir 2 al azar
        List<Song> allSongs = new ArrayList<>();
        for (Long playlistId : playlistIds) {
            allSongs.addAll(songRepository.findByPlaylistId(playlistId));
        }

        Collections.shuffle(allSongs);
        List<Song> picked = allSongs.subList(0, Math.min(2, allSongs.size()));

        // Crear playlist en Spotify (endpoint actualizado Feb 2026)
        String createBody = "{\"name\":\"Splitify Test\",\"public\":false,\"description\":\"Playlist de prueba creada por Splitify\"}";

        SpotifyDto.CreatePlaylistResponse created = restClient.post()
                .uri("/me/playlists")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(createBody)
                .retrieve()
                .body(SpotifyDto.CreatePlaylistResponse.class);

        // Agregar canciones (endpoint actualizado Feb 2026: /tracks -> /items)
        List<String> uris = picked.stream()
                .map(s -> "spotify:track:" + s.getSpotifyId())
                .toList();

        String addTracksBody = "{\"uris\":" + uris.stream()
                .map(u -> "\"" + u + "\"")
                .collect(Collectors.joining(",", "[", "]")) + "}";

        restClient.post()
                .uri("/playlists/{playlistId}/items", created.id())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(addTracksBody)
                .retrieve()
                .toBodilessEntity();

        // Guardar en BD como playlist de Splitify
        Playlist playlist = Playlist.builder()
                .spotifyId(created.id())
                .name("Splitify Test")
                .totalTracks(picked.size())
                .userId(userId)
                .splitify(true)
                .build();
        playlist = playlistRepository.save(playlist);

        for (Song s : picked) {
            songRepository.save(Song.builder()
                    .spotifyId(s.getSpotifyId())
                    .name(s.getName())
                    .artist(s.getArtist())
                    .playlist(playlist)
                    .build());
        }

        return toDto(playlist);
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

    private Playlist syncLikedSongs(RestClient restClient, String userId) {
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
            saveTracks(response.items(), likedSongs);
        }

        return likedSongs;
    }

    private void syncPlaylistTracks(RestClient restClient, Playlist playlist) {
        SpotifyDto.TracksResponse response = restClient.get()
                .uri("/playlists/{id}/items?limit=10", playlist.getSpotifyId())
                .retrieve()
                .body(SpotifyDto.TracksResponse.class);

        if (response != null && response.items() != null && !response.items().isEmpty()) {
            saveTracks(response.items(), playlist);
        }
    }

    private void saveTracks(List<SpotifyDto.TrackWrapper> items, Playlist playlist) {
        for (SpotifyDto.TrackWrapper wrapper : items) {
            if (wrapper.track() == null) continue;

            SpotifyDto.Track track = wrapper.track();
            String artistName = track.artists() != null && !track.artists().isEmpty()
                    ? track.artists().stream()
                        .map(SpotifyDto.Artist::name)
                        .collect(Collectors.joining(", "))
                    : "Desconocido";

            Song song = Song.builder()
                    .spotifyId(track.id())
                    .name(track.name())
                    .artist(artistName)
                    .playlist(playlist)
                    .build();
            songRepository.save(song);
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
