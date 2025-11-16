package com.mentoai.mentoai.controller.dto;

public record RoleFitSimulationResponse(
        double baseScore,
        double newScore,
        double delta,
        RoleFitResponse.Breakdown breakdownDelta
) {
}


