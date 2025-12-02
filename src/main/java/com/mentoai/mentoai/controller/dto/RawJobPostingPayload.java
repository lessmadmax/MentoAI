package com.mentoai.mentoai.controller.dto;

/**
 * 외부 데이터 소스에서 수집한 원시 채용 공고 정보를 표현하는 DTO.
 */
public record RawJobPostingPayload(
        String title,
        String company,
        String etc,
        String imgUrl,
        String link
) {
}

