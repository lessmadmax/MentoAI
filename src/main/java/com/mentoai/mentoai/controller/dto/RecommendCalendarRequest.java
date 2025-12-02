package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mentoai.mentoai.config.jackson.FlexibleLocalDateTimeDeserializer;
import com.mentoai.mentoai.entity.CalendarEventType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RecommendCalendarRequest(
        @NotNull Long userId,
        @NotNull CalendarEventType eventType,
        Long activityId,
        Long jobPostingId,
        Long recommendLogId,
        @NotNull
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime startAt,
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime endAt,
        Integer alertMinutes
) {
}


