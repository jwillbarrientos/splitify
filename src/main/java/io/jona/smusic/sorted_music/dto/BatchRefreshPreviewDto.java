package io.jona.smusic.sorted_music.dto;

// Preview de refresh por playlist dentro de un batch. Incluye el nombre para que
// el frontend pueda agrupar las canciones por playlist en el modal de conflictos.
public record BatchRefreshPreviewDto(Long playlistId, String playlistName, RefreshPreviewDto preview) {}
