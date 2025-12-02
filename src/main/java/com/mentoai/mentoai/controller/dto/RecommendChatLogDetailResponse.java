package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RecommendChatLogDetailResponse(
        Long logId,
        String targetRoleId,
        String userQuery,
        String ragPrompt,
        String geminiResponse,
        JsonNode requestPayload,
        JsonNode responsePayload,
        String modelName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

