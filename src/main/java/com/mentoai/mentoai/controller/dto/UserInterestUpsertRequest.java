package com.mentoai.mentoai.controller.dto;

public record UserInterestUpsertRequest(
    String tagName,
    Double weight  // 기본값 0.7
) {
    public Double getWeightOrDefault() {
        return weight != null ? weight : 0.7;
    }
}

