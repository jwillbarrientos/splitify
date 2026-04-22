package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Caché global de tags de Last.fm por canción específica (endpoint `track.gettoptags`).
 *
 * Los tags por canción son mucho más ricos que los por artista para la clasificación:
 * incluyen señales directas de idioma ("spanish", "paraguayo", "french") y de género
 * a nivel de la pista concreta (ej: una canción de rock puede tener tag "ballad",
 * "acoustic", "2015"), cosa que los tags agregados del artista no capturan.
 *
 * Indexada por `spotifyTrackId` (el ID estable de Spotify). Complementa a `ArtistGenres`
 * que se usa como fallback cuando Last.fm no tiene tags para la canción específica.
 */
@Entity
@Table(name = "track_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackTags extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String spotifyTrackId;

    @Column(length = 2000)
    private String tags;
}
