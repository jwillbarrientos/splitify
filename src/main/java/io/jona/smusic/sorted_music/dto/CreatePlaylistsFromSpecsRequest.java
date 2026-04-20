package io.jona.smusic.sorted_music.dto;

import java.util.List;

// Segundo paso del flujo "Organizar por criterios": el frontend envía los specs
// (con los nombres posiblemente editados) junto con las playlists origen seleccionadas.
public record CreatePlaylistsFromSpecsRequest(List<Long> playlistIds, List<PlaylistCreateSpec> playlists) {}
