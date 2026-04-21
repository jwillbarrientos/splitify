package io.jona.smusic.sorted_music.dto;

import java.util.List;

// newFromSources: canciones que están en las fuentes y se agregarán al hijo.
// removedByUser: el usuario quitó estas canciones del hijo en Spotify pero siguen en las fuentes → preguntar si restaurar.
// lostSourceSongs: canciones que siguen en el hijo pero su fuente las quitó (o la fuente entera se vació/borró) → preguntar si quitar del hijo.
public record RefreshPreviewDto(
        List<SongDto> newFromSources,
        List<SongDto> removedByUser,
        List<SongDto> lostSourceSongs,
        boolean hasChanges
) {}
