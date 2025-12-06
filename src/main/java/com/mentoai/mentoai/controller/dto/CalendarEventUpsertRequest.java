package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mentoai.mentoai.config.jackson.FlexibleLocalDateTimeDeserializer;
import com.mentoai.mentoai.entity.CalendarEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CalendarEventUpsertRequest(
        @NotNull(message = "eventType은 필수입니다.")
        CalendarEventType eventType,
        @Size(max = 255, message = "eventTitle은 255자 이하여야 합니다.")
        String eventTitle,
        Long activityId,
        Long jobPostingId,
        Long recommendLogId,
        @NotNull(message = "startAt은 필수입니다.")
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime startAt,
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime endAt,
        Integer alertMinutes
) {
}
