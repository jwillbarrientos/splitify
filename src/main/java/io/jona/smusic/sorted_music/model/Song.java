package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "song")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String spotifyId;

    private String name;

    private String artist;

    private Integer releaseYear;

    private String genre;

    private String language;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;
}
