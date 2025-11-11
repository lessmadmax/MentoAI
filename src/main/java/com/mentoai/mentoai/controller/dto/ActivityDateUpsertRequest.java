package com.mentoai.mentoai.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ActivityDateUpsertRequest(
        @NotBlank String dateType,
        @NotNull LocalDateTime dateValue
) {
}



