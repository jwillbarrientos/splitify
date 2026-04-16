package io.jona.smusic.sorted_music.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "playlist")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Playlist extends BaseEntity {

    private String spotifyId;

    private String name;

    private String imageUrl;

    private int totalTracks;

    private String userId;

    @Builder.Default
    private boolean splitify = false;

    private String snapshotId;

    private String filterType;

    private String filterValue;
}
