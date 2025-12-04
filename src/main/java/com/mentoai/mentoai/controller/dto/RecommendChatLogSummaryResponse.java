package com.mentoai.mentoai.controller.dto;

import java.time.OffsetDateTime;

public record RecommendChatLogSummaryResponse(
        Long logId,
        String targetRoleId,
        String userQuery,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

