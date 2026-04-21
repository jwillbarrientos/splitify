package io.jona.smusic.sorted_music.dto;

import java.util.List;

// Request body para actualizar una playlist Splitify.
// restoredSongIds: spotifyIds de canciones que el usuario había quitado manualmente y quiere re-agregar.
// removedSongIds: spotifyIds de canciones cuyo origen desapareció y el usuario eligió quitar del hijo.
// Cualquiera de los dos null/vacío = no hacer esa operación.
public record RefreshRequest(List<String> restoredSongIds, List<String> removedSongIds) {}
