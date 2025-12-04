package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_posting_roles")
@Getter
@Setter
@NoArgsConstructor
public class JobPostingRoleEntity {

    @EmbeddedId
    private JobPostingRoleId id = new JobPostingRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jobId")
    private JobPostingEntity jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("targetRoleId")
    private TargetRoleEntity targetRole;

    @Column(name = "relevance")
    private Double relevance;
}

