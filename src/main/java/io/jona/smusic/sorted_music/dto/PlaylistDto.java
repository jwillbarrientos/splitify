package io.jona.smusic.sorted_music.dto;

public record PlaylistDto(Long id, String spotifyId, String name, String imageUrl, int totalTracks) {}
