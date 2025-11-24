package com.mentoai.mentoai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "activity_target_roles")
@Getter
@Setter
@NoArgsConstructor
public class ActivityTargetRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private ActivityEntity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", referencedColumnName = "role_id", nullable = false)
    private TargetRoleEntity targetRole;

    @Column(name = "activity_id", insertable = false, updatable = false)
    private Long activityId;

    @Column(name = "role_id", insertable = false, updatable = false)
    private String targetRoleId;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "matched_requirements", columnDefinition = "jsonb")
    private String matchedRequirements;

    @Column(name = "matched_preferences", columnDefinition = "jsonb")
    private String matchedPreferences;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum SyncStatus {
        PENDING,
        SYNCED,
        FAILED
    }
}

