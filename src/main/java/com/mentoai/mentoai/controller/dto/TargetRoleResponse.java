package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

public record TargetRoleResponse(
        String roleId,
        String name,
        Map<String, Double> requiredSkills,
        Map<String, Double> bonusSkills,
        Map<String, Double> majorMapping,
        String expectedSeniority,
        List<String> recommendedCerts,
        List<String> keywords,
        OffsetDateTime updatedAt
) {
}




