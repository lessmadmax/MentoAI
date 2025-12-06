package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mentoai.mentoai.config.jackson.FlexibleLocalDateTimeDeserializer;
import com.mentoai.mentoai.entity.CalendarEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record RecommendCalendarRequest(
        @NotNull Long userId,
        @NotNull CalendarEventType eventType,
        @Size(max = 255, message = "eventTitle은 255자 이하여야 합니다.")
        String eventTitle,
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


