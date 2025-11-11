package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record TagUpsertRequest(
        @NotBlank String tagName,
        @NotBlank String tagType
) {
}



