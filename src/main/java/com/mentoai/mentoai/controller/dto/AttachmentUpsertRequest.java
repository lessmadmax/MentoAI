package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record AttachmentUpsertRequest(
        @NotBlank String fileType,
        @NotBlank String fileUrl,
        String ocrText
) {
}



