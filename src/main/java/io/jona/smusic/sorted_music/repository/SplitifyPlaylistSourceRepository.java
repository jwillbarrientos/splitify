package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.SplitifyPlaylistSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SplitifyPlaylistSourceRepository extends JpaRepository<SplitifyPlaylistSource, Long> {

    List<SplitifyPlaylistSource> findBySplitifyPlaylistId(Long splitifyPlaylistId);

    @Transactional
    void deleteBySplitifyPlaylistId(Long splitifyPlaylistId);
}
