package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record JobPostingResponse(
        Long jobId,
        String companyName,
        String title,
        String rank,
        String jobSector,
        String employmentType,
        String workPlace,
        String careerLevel,
        String educationLevel,
        String description,
        String requirements,
        String benefits,
        String link,
        OffsetDateTime deadline,
        OffsetDateTime registeredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<JobPostingSkillResponse> skills,
        List<JobPostingRoleResponse> targetRoles
) {

    public record JobPostingSkillResponse(
            String skillName,
            String proficiency
    ) {
    }

    public record JobPostingRoleResponse(
            String targetRoleId,
            String targetRoleName,
            Double relevance
    ) {
    }
}


