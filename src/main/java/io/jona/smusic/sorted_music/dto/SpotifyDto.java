package io.jona.smusic.sorted_music.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SpotifyDto {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistsResponse(List<PlaylistItem> items, String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaylistItem(String id, String name, List<Image> images,
                               @JsonAlias("items") TracksRef tracks,
                               Owner owner, boolean collaborative) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TracksRef(int total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TracksResponse(List<TrackWrapper> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrackWrapper(@JsonAlias("item") Track track) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(String id, String name, List<Artist> artists) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Artist(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LikedTracksResponse(List<TrackWrapper> items, int total) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreatePlaylistResponse(String id, String name) {}
}
