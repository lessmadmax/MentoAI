package com.mentoai.mentoai.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public record JobPostingUpsertRequest(
        @NotBlank(message = "회사명을 입력해 주세요.")
        String companyName,

        @NotBlank(message = "공고 제목을 입력해 주세요.")
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
        @Valid
        List<JobPostingSkillPayload> skills,
        @Valid
        List<JobPostingRolePayload> targetRoles
) {

    public record JobPostingSkillPayload(
            @NotBlank(message = "스킬명을 입력해 주세요.")
            String skillName,
            String proficiency
    ) {
    }

    public record JobPostingRolePayload(
            @NotBlank(message = "타겟 직무 ID를 입력해 주세요.")
            String targetRoleId,
            Double relevance
    ) {
    }
}


