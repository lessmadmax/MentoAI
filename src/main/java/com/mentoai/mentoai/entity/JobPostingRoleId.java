package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class JobPostingRoleId implements Serializable {

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "target_role_id")
    private String targetRoleId;
}

