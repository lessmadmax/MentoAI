package com.mentoai.mentoai.controller.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long attachmentId,
        String fileType,
        String fileUrl,
        String ocrText,
        LocalDateTime createdAt
) {
}


