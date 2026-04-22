package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.ArtistGenres;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistGenresRepository extends JpaRepository<ArtistGenres, Long> {

    List<ArtistGenres> findByArtistNameIn(List<String> artistNames);
}
