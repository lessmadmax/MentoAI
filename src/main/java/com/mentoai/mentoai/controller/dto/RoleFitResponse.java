package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record RoleFitResponse(
        String target,
        double roleFitScore,
        Breakdown breakdown,
        List<MissingSkill> missingSkills,
        List<String> recommendations
) {
    public record Breakdown(
            double skillFit,
            double experienceFit,
            double educationFit,
            double evidenceFit
    ) {
    }

    public record MissingSkill(String skill, double impact) {
    }
}


