package io.jona.smusic.sorted_music.controller;

import io.jona.smusic.sorted_music.dto.AvailableFiltersDto;
import io.jona.smusic.sorted_music.dto.BatchRefreshPreviewDto;
import io.jona.smusic.sorted_music.dto.BatchRefreshRequest;
import io.jona.smusic.sorted_music.dto.CreateCustomPlaylistRequest;
import io.jona.smusic.sorted_music.dto.CreatePlaylistsFromSpecsRequest;
import io.jona.smusic.sorted_music.dto.CreatePlaylistsRequest;
import io.jona.smusic.sorted_music.dto.PlaylistCreateSpec;
import io.jona.smusic.sorted_music.dto.PlaylistDto;
import io.jona.smusic.sorted_music.dto.RefreshPreviewDto;
import io.jona.smusic.sorted_music.dto.RefreshRequest;
import io.jona.smusic.sorted_music.dto.SongDto;
import io.jona.smusic.sorted_music.repository.PlaylistRepository;
import io.jona.smusic.sorted_music.repository.SongRepository;
import io.jona.smusic.sorted_music.service.SpotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // Paso 1 del flujo "Organizar por criterios": devuelve los nombres default que se usarían.
    // El frontend muestra un modal para que el usuario los edite si quiere.
    @PostMapping("/create/combined/preview")
    public List<PlaylistCreateSpec> previewOrganizedPlaylists(@RequestBody CreatePlaylistsRequest request) {
        return spotifyService.previewOrganizedPlaylists(
                request.playlistIds(),
                request.byLanguage(), request.byGenre(), request.byReleaseDate());
    }

    // Paso 2: crea las playlists con los nombres finales (posiblemente editados).
    @PostMapping("/create/combined/confirm")
    public List<PlaylistDto> createOrganizedPlaylistsFromSpecs(OAuth2AuthenticationToken authentication,
                                                                 @RequestBody CreatePlaylistsFromSpecsRequest request) {
        return spotifyService.createOrganizedPlaylistsFromSpecs(
                authentication, request.playlistIds(), request.playlists());
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
            @RequestBody(required = false) RefreshRequest request,
            OAuth2AuthenticationToken authentication) {
        Set<String> restoredIds = (request != null && request.restoredSongIds() != null)
                ? new HashSet<>(request.restoredSongIds()) : Set.of();
        PlaylistDto result = spotifyService.refreshSplitifyPlaylist(authentication, id, restoredIds);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/splitify/batch/refresh")
    public List<PlaylistDto> refreshSplitifyPlaylists(
            @RequestBody BatchRefreshRequest request,
            OAuth2AuthenticationToken authentication) {
        List<PlaylistDto> results = new ArrayList<>();
        if (request == null || request.items() == null) return results;
        for (BatchRefreshRequest.Item item : request.items()) {
            Set<String> restored = item.restoredSongIds() != null
                    ? new HashSet<>(item.restoredSongIds()) : Set.of();
            PlaylistDto result = spotifyService.refreshSplitifyPlaylist(
                    authentication, item.playlistId(), restored);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    // Preview batch: devuelve qué cambios ocurrirían en cada playlist del batch,
    // incluyendo el nombre para que el frontend pueda agruparlas en el modal de conflictos.
    @PostMapping("/splitify/batch/refresh/preview")
    public List<BatchRefreshPreviewDto> previewBatchRefresh(
            @RequestBody List<Long> ids,
            OAuth2AuthenticationToken authentication) {
        List<BatchRefreshPreviewDto> result = new ArrayList<>();
        String userId = authentication.getName();
        for (Long id : ids) {
            playlistRepository.findById(id)
                    .filter(p -> p.isSplitify() && userId.equals(p.getUserId()))
                    .ifPresent(p -> {
                        RefreshPreviewDto preview = spotifyService.previewRefresh(authentication, id);
                        result.add(new BatchRefreshPreviewDto(id, p.getName(), preview));
                    });
        }
        return result;
    }

    @PutMapping("/splitify/{id}/rename")
    public PlaylistDto renameSplitifyPlaylist(@PathVariable Long id,
                                               @RequestBody RenamePlaylistRequest request,
                                               OAuth2AuthenticationToken authentication) {
        return spotifyService.renameSplitifyPlaylist(authentication, id, request.name());
    }

    @PutMapping("/splitify/{id}/image")
    public PlaylistDto updateSplitifyPlaylistImage(@PathVariable Long id,
                                                    @RequestBody UpdatePlaylistImageRequest request,
                                                    OAuth2AuthenticationToken authentication) {
        return spotifyService.updateSplitifyPlaylistImage(authentication, id, request.imageBase64());
    }

    public record RenamePlaylistRequest(String name) {}
    public record UpdatePlaylistImageRequest(String imageBase64) {}

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
