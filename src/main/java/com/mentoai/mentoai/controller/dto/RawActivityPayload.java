package com.mentoai.mentoai.controller.dto;

/**
 * 외부 크롤러 등에서 수집한 원시 활동 데이터를 표현하는 DTO.
 */
public record RawActivityPayload(
        String title,
        String url,
        String category,
        String organization,
        String deadline
) {
}

