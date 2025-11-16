package com.mentoai.mentoai.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record RoleFitSimulationRequest(
        @NotBlank String target,
        List<@Valid SimulationSkill> addSkills,
        List<String> addCertifications,
        List<@Valid SimulationExperience> addExperiences
) {

    public record SimulationSkill(
            @NotBlank String name,
            String level
    ) {
    }

    public record SimulationExperience(
            @NotBlank String type,
            Integer durationMonths
    ) {
    }
}


