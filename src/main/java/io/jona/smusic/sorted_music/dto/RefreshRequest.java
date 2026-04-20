package io.jona.smusic.sorted_music.dto;

import java.util.List;

// Request body para actualizar una playlist Splitify.
// restoredSongIds: spotifyIds de canciones que el usuario había quitado manualmente y quiere re-agregar.
// Vacío/null = no restaurar ninguna (equivale al antiguo restoreRemoved=false).
public record RefreshRequest(List<String> restoredSongIds) {}
