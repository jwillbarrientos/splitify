package io.jona.smusic.sorted_music.dto;

import java.util.List;

public record CreateCustomPlaylistRequest(
        List<Long> playlistIds,
        List<String> languages,
        List<String> genres,
        List<String> artists,
        String name
) {}
