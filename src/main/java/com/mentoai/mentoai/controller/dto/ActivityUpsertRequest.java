package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record ActivityUpsertRequest(
        @NotBlank String title,
        String summary,
        String content,
        @NotBlank String type,
        String organizer,
        String location,
        String url,
        Boolean isCampus,
        String status,
        LocalDateTime publishedAt,
        List<String> tags,
        List<ActivityDateUpsertRequest> dates
) {
}


