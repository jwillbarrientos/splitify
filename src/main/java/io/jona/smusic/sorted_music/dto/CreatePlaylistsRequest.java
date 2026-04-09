package io.jona.smusic.sorted_music.dto;

import java.util.List;

public record CreatePlaylistsRequest(List<Long> playlistIds, boolean byLanguage, boolean byGenre, boolean byReleaseDate) {}
