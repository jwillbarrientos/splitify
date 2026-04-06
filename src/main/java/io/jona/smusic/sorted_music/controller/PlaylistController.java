package io.jona.smusic.sorted_music.controller;

import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.SongDto;
import io.jona.smusic.sorted_music.repository.PlaylistRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import io.jona.smusic.sorted_music.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final SpotifyService spotifyService;
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;

    @PostMapping("/sync")
    public List<PlaylistDto> sync(OAuth2AuthenticationToken authentication) {
        return spotifyService.syncPlaylists(authentication);
    }

    @GetMapping
    public List<PlaylistDto> getPlaylists(OAuth2AuthenticationToken authentication) {
        return spotifyService.getUserPlaylists(authentication.getName());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistDto> getPlaylist(@PathVariable Long id) {
        return playlistRepository.findById(id)
                .map(p -> ResponseEntity.ok(new PlaylistDto(
                        p.getId(), p.getSpotifyId(), p.getName(), p.getImageUrl(), p.getTotalTracks())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/songs")
    public List<SongDto> getSongs(@PathVariable Long id) {
        return songRepository.findByPlaylistId(id).stream()
                .map(s -> new SongDto(
                        s.getId(), s.getSpotifyId(), s.getName(), s.getArtist(),
                        s.getReleaseYear(), s.getGenre(), s.getLanguage()))
                .toList();
    }
}
