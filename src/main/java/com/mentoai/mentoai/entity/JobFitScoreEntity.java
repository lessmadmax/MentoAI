package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "job_fit_scores")
@Getter
@Setter
@NoArgsConstructor
public class JobFitScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "score_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "target_role_id")
    private String targetRoleId;

    @Column(name = "job_url", columnDefinition = "TEXT")
    private String jobUrl;

    @Column(name = "total_score", nullable = false)
    private Double totalScore;

    @Column(name = "skill_fit")
    private Double skillFit;

    @Column(name = "experience_fit")
    private Double experienceFit;

    @Column(name = "education_fit")
    private Double educationFit;

    @Column(name = "evidence_fit")
    private Double evidenceFit;

    @Column(name = "missing_skills", columnDefinition = "jsonb")
    private String missingSkillsJson;

    @Column(name = "recommendations", columnDefinition = "jsonb")
    private String recommendationsJson;

    @Column(name = "improvements", columnDefinition = "jsonb")
    private String improvementsJson;

    @Column(name = "requirements", columnDefinition = "jsonb")
    private String requirementsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

