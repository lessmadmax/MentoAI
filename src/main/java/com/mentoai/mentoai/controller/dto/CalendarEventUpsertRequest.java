package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.entity.CalendarEventType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CalendarEventUpsertRequest(
        @NotNull(message = "eventType은 필수입니다.")
        CalendarEventType eventType,
        Long activityId,
        Long jobPostingId,
        Long recommendLogId,
        @NotNull(message = "startAt은 필수입니다.")
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer alertMinutes
) {
}
