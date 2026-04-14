package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "splitify_playlist_source")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitifyPlaylistSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "splitify_playlist_id")
    private Playlist splitifyPlaylist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_playlist_id")
    private Playlist sourcePlaylist;
}
