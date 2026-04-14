package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "song_classification")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SongClassification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String spotifyId;

    private String genre;

    private String language;
}
