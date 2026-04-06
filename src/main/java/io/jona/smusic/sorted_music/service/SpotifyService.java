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
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
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

        // Borrar datos viejos del usuario
        List<Playlist> oldPlaylists = playlistRepository.findByUserId(userId);
        for (Playlist old : oldPlaylists) {
            songRepository.deleteByPlaylistId(old.getId());
        }
        playlistRepository.deleteByUserId(userId);

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

    public List<PlaylistDto> getUserPlaylists(String userId) {
        return playlistRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
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
