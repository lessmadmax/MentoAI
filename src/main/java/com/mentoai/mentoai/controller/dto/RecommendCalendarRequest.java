package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.entity.CalendarEventType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RecommendCalendarRequest(
        @NotNull Long userId,
        @NotNull CalendarEventType eventType,
        Long activityId,
        Long jobPostingId,
        Long recommendLogId,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Integer alertMinutes
) {
}


