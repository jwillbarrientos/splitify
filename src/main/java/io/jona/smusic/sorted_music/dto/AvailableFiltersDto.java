package io.jona.smusic.sorted_music.dto;

import java.util.List;

public record AvailableFiltersDto(List<String> languages, List<String> genres, List<String> artists) {}
