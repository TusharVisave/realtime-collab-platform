package com.collab.platform.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "conflict_records", indexes = {
    @Index(name = "idx_conflict_room", columnList = "roomId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictRecord {

    @Id
    @Column(nullable = false, length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String roomId;

    @Column(nullable = false, length = 64)
    private String itemId;

    @Column(length = 200)
    private String itemTitle;

    @Column(nullable = false, length = 80)
    private String winningUser;

    @Column(nullable = false, length = 80)
    private String rejectedUser;

    @Column(nullable = false)
    private Long winningVersion;

    @Column(nullable = false)
    private Long rejectedVersion;

    @Column(nullable = false)
    private Long winningTimestamp;

    @Column(nullable = false)
    private Long rejectedTimestamp;

    @Column(nullable = false, length = 250)
    private String resolutionReason;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
