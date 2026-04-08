package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "playlist")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String spotifyId;

    private String name;

    private String imageUrl;

    private int totalTracks;

    private String userId;

    @Builder.Default
    private boolean splitify = false;
}
