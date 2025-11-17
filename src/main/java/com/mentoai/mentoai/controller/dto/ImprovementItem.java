package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record ImprovementItem(
        String type,
        ActivityResponse activity,
        double expectedScoreDelta,
        List<String> affects,
        String reason
) {
}




