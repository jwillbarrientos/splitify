package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserId(String userId);

    List<Playlist> findByUserIdAndSplitify(String userId, boolean splitify);

    @Transactional
    void deleteByUserId(String userId);
}
