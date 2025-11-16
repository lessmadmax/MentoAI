package com.mentoai.mentoai.controller.dto;

import java.time.LocalDateTime;

public record CalendarEventResponse(
        Long eventId,
        Long userId,
        Long activityId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer alertMinutes,
        LocalDateTime createdAt
) {
}


