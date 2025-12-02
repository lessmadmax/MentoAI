package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.mentoai.mentoai.config.jackson.FlexibleLocalDateTimeDeserializer;
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
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime startAt,
        @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
        LocalDateTime endAt,
        Integer alertMinutes
) {
}
