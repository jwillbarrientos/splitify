package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findByPlaylistId(Long playlistId);

    @Transactional
    void deleteByPlaylistId(Long playlistId);
}
