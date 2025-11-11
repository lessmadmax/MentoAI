package com.mentoai.mentoai.controller.dto;

import java.time.LocalDateTime;

public record ActivityDateResponse(
        Long dateId,
        String dateType,
        LocalDateTime dateValue
) {
}



