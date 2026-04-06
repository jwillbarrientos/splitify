package io.jona.smusic.sorted_music.dto;

public record SongDto(Long id, String spotifyId, String name, String artist,
                      Integer releaseYear, String genre, String language) {}
