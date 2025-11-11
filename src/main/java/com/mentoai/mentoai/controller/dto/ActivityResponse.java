package com.mentoai.mentoai.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ActivityResponse(
        Long activityId,
        String title,
        String summary,
        String content,
        String type,
        String organizer,
        String location,
        String url,
        Boolean isCampus,
        String status,
        String vectorDocId,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ActivityDateResponse> dates,
        List<TagResponse> tags,
        List<AttachmentResponse> attachments
) {
}



