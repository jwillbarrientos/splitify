package io.jona.smusic.sorted_music.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

import java.util.concurrent.atomic.AtomicLong;

@MappedSuperclass
public class BaseEntity {

    @Id
    @Getter @Setter
    private Long id;

    private static AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());
    @PrePersist
    protected void onCreate() {
        id = idSeq.getAndIncrement();
    }
}
