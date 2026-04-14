package io.jona.smusic.sorted_music.dto;

import java.util.List;

public record RefreshPreviewDto(
        List<SongDto> newFromSources,
        List<SongDto> removedByUser,
        boolean hasChanges
) {}
