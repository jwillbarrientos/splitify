package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.TrackTags;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackTagsRepository extends JpaRepository<TrackTags, Long> {

    List<TrackTags> findBySpotifyTrackIdIn(List<String> spotifyTrackIds);
}
