package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.SongClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SongClassificationRepository extends JpaRepository<SongClassification, Long> {

    Optional<SongClassification> findBySpotifyId(String spotifyId);
}
