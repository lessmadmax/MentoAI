package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotNull;

public record JobRecommendRequest(
        @NotNull(message = "userId는 필수입니다.")
        Long userId,
        Integer limit,
        String query
) {
    public int fetchSize() {
        int defaultSize = 200;
        if (limit == null || limit <= 0) {
            return defaultSize;
        }
        return Math.min(Math.max(limit, 1), 500);
    }
}


