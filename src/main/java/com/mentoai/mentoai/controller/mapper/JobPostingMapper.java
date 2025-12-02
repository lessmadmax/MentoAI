package com.mentoai.mentoai.controller.mapper;

import com.mentoai.mentoai.controller.dto.JobPostingResponse;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.entity.JobPostingRoleEntity;
import com.mentoai.mentoai.entity.JobPostingSkillEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class JobPostingMapper {

    private JobPostingMapper() {
    }

    public static JobPostingResponse toResponse(JobPostingEntity entity) {
        return new JobPostingResponse(
                entity.getId(),
                entity.getCompanyName(),
                entity.getTitle(),
                entity.getRank(),
                entity.getJobSector(),
                entity.getEmploymentType(),
                entity.getWorkPlace(),
                entity.getCareerLevel(),
                entity.getEducationLevel(),
                entity.getDescription(),
                entity.getRequirements(),
                entity.getBenefits(),
                entity.getLink(),
                entity.getDeadline(),
                entity.getRegisteredAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                mapSkills(entity.getSkills()),
                mapTargetRoles(entity.getTargetRoles())
        );
    }

    private static List<JobPostingResponse.JobPostingSkillResponse> mapSkills(List<JobPostingSkillEntity> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        return skills.stream()
                .map(skill -> new JobPostingResponse.JobPostingSkillResponse(
                        skill.getSkillName(),
                        skill.getProficiency()
                ))
                .toList();
    }

    private static List<JobPostingResponse.JobPostingRoleResponse> mapTargetRoles(List<JobPostingRoleEntity> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return targetRoles.stream()
                .map(role -> new JobPostingResponse.JobPostingRoleResponse(
                        Optional.ofNullable(role.getTargetRole())
                                .map(tr -> tr.getRoleId())
                                .orElse(null),
                        Optional.ofNullable(role.getTargetRole())
                                .map(tr -> tr.getName())
                                .orElse(null),
                        role.getRelevance()
                ))
                .toList();
    }
}


