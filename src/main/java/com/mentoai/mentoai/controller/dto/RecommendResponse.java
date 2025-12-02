package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record RecommendResponse(
        List<RecommendItem> items
) {
    public record RecommendItem(
            ActivityResponse activity,
            Double score,
            String reason,
            Double systemScore,
            Double expectedScoreIncrease,
            Double expectedScoreAfterCompletion
    ) {}
}

