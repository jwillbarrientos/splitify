package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "song")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Song extends BaseEntity {

    private String spotifyId;

    private String name;

    private String artist;

    private Integer releaseYear;

    private String releaseDate;

    private String genre;

    private String language;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean excluded = false;

    @Column(columnDefinition = "boolean default false")
    @Builder.Default
    private boolean manuallyAdded = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;
}
