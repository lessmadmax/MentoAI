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
@Table(name = "job_posting_skills")
@Getter
@Setter
@NoArgsConstructor
public class JobPostingSkillEntity {

    @EmbeddedId
    private JobPostingSkillId id = new JobPostingSkillId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jobId")
    private JobPostingEntity jobPosting;

    @Column(name = "skill_name", insertable = false, updatable = false)
    private String skillName;

    @Column(name = "proficiency")
    private String proficiency;
}

