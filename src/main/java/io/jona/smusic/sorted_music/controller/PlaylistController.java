package io.jona.smusic.sorted_music.controller;

import io.jona.smusic.sorted_music.dto.AvailableFiltersDto;
import io.jona.smusic.sorted_music.dto.CreateCustomPlaylistRequest;
import io.jona.smusic.sorted_music.dto.CreatePlaylistsRequest;
import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.RefreshPreviewDto;
import io.jona.smusic.sorted_music.dto.SongDto;
import io.jona.smusic.sorted_music.repository.PlaylistRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import io.jona.smusic.sorted_music.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(OAuth2AuthenticationToken authentication) {
        spotifyService.deleteUserPlaylists(authentication.getName());
        return ResponseEntity.noContent().build();
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
                .filter(s -> !s.isExcluded())
                .map(s -> new SongDto(
                        s.getId(), s.getSpotifyId(), s.getName(), s.getArtist(),
                        s.getReleaseYear(), s.getGenre(), s.getLanguage()))
                .toList();
    }

    @PostMapping("/create/combined")
    public List<PlaylistDto> createOrganizedPlaylists(OAuth2AuthenticationToken authentication,
                                                       @RequestBody CreatePlaylistsRequest request) {
        return spotifyService.createOrganizedPlaylists(
                authentication, request.playlistIds(),
                request.byLanguage(), request.byGenre(), request.byReleaseDate());
    }

    @GetMapping("/available-filters")
    public AvailableFiltersDto getAvailableFilters(@RequestParam("playlistIds") List<Long> playlistIds) {
        return spotifyService.getAvailableFilters(playlistIds);
    }

    @PostMapping("/create/custom")
    public PlaylistDto createCustomPlaylist(OAuth2AuthenticationToken authentication,
                                             @RequestBody CreateCustomPlaylistRequest request) {
        return spotifyService.createCustomPlaylist(
                authentication,
                request.playlistIds(),
                request.languages(),
                request.genres(),
                request.artists(),
                request.name());
    }

    @GetMapping("/splitify")
    public List<PlaylistDto> getSplitifyPlaylists(OAuth2AuthenticationToken authentication) {
        return spotifyService.getSplitifyPlaylists(authentication.getName());
    }

    @GetMapping("/splitify/{id}/refresh/preview")
    public RefreshPreviewDto previewRefresh(@PathVariable Long id,
                                            OAuth2AuthenticationToken authentication) {
        return spotifyService.previewRefresh(authentication, id);
    }

    @PutMapping("/splitify/{id}/refresh")
    public ResponseEntity<PlaylistDto> refreshSplitifyPlaylist(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean restoreRemoved,
            OAuth2AuthenticationToken authentication) {
        PlaylistDto result = spotifyService.refreshSplitifyPlaylist(authentication, id, restoreRemoved);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/splitify/batch/refresh")
    public List<PlaylistDto> refreshSplitifyPlaylists(
            @RequestBody List<Long> ids,
            @RequestParam(defaultValue = "false") boolean restoreRemoved,
            OAuth2AuthenticationToken authentication) {
        List<PlaylistDto> results = new ArrayList<>();
        for (Long id : ids) {
            PlaylistDto result = spotifyService.refreshSplitifyPlaylist(authentication, id, restoreRemoved);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    @DeleteMapping("/splitify/{id}")
    public ResponseEntity<Void> deleteSplitifyPlaylist(@PathVariable Long id,
                                                        OAuth2AuthenticationToken authentication) {
        spotifyService.deleteSplitifyPlaylist(authentication, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/splitify")
    public ResponseEntity<Void> deleteSplitifyPlaylists(@RequestBody List<Long> ids,
                                                         OAuth2AuthenticationToken authentication) {
        spotifyService.deleteSplitifyPlaylists(authentication, ids);
        return ResponseEntity.noContent().build();
    }
}
