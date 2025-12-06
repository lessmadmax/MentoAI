package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.entity.CalendarEventType;

import java.time.LocalDateTime;

public record CalendarEventResponse(
        Long eventId,
        Long userId,
        CalendarEventType eventType,
        Long activityId,
        Long jobPostingId,
        Long recommendLogId,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer alertMinutes,
        LocalDateTime createdAt,
        String eventTitle,
        String activityTitle,
        String jobPostingTitle,
        String jobPostingCompany
) {
}
