package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public record TargetRoleUpsertRequest(
        @NotBlank String roleId,
        @NotBlank String name,
        String expectedSeniority,
        Map<String, Double> requiredSkills,
        Map<String, Double> bonusSkills,
        Map<String, Double> majorMapping,
        List<String> recommendedCerts
) {
}




