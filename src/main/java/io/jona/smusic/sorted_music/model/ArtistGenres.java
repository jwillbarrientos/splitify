package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Caché global de los tags de género de Last.fm por artista. Se usa como contexto en el
 * prompt de ChatGPT para que no tenga que adivinar el género a partir del nombre solo.
 *
 * Indexada por nombre de artista (no por Spotify ID) porque desde febrero 2026 Spotify
 * removió el endpoint /v1/artists batch y el campo `genres` de los artistas. Last.fm los
 * consulta por nombre (`method=artist.gettoptags`).
 */
@Entity
@Table(name = "artist_genres")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtistGenres extends BaseEntity {

    @Column(unique = true, nullable = false, length = 500)
    private String artistName;

    // Tags de Last.fm separados por coma (ej: "rock, indie rock, alternative"). Puede estar
    // vacía si Last.fm no tiene tags para ese artista o la búsqueda falló. Se guarda igual
    // para no re-consultar Last.fm en futuros syncs.
    @Column(length = 2000)
    private String genres;
}
