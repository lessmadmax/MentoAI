package com.mentoai.mentoai.controller.dto;

public record UserInterestResponse(
    String tagName,
    Double weight  // 0.0 ~ 1.0 (스펙에서는 float, example: 0.7)
) {
}

