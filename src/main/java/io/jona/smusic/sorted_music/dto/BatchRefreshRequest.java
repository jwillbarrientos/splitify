package io.jona.smusic.sorted_music.dto;

import java.util.List;

// Request body para refresh batch. Cada item indica una playlist y qué canciones
// restaurar o quitar en ella (puede ser distinto por playlist).
public record BatchRefreshRequest(List<Item> items) {
    public record Item(Long playlistId, List<String> restoredSongIds, List<String> removedSongIds) {}
}
