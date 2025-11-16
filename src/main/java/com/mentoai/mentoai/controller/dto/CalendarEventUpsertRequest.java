package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CalendarEventUpsertRequest(
        Long activityId,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Integer alertMinutes
) {
}


