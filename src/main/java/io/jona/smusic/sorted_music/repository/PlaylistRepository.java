package io.jona.smusic.sorted_music.repository;

import io.jona.smusic.sorted_music.model.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    @Query("SELECT p FROM Playlist p WHERE p.userId = :userId AND p.splitify = :splitify " +
           "ORDER BY CASE WHEN p.spotifyId = 'liked_songs' THEN 0 ELSE 1 END, p.id ASC")
    List<Playlist> findByUserIdAndSplitifyOrderByIdAsc(@Param("userId") String userId,
                                                        @Param("splitify") boolean splitify);
}
