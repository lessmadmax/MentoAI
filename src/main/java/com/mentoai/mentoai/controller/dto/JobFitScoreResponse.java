package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record JobFitScoreResponse(
        Long jobId,
        Long userId,
        String jobTitle,
        String companyName,
        double totalScore,
        RoleFitResponse.Breakdown breakdown,
        List<RoleFitResponse.MissingSkill> missingSkills,
        List<String> recommendations,
        List<ImprovementItem> improvements,
        JobRequirementPayload requirements,
        boolean cached,
        OffsetDateTime evaluatedAt
) {
}

