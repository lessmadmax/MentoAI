package com.mentoai.mentoai.controller.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendRequest(
    Long userId,
    String query,  // 자연어 질의
    Integer topK,  // 추천 개수 (기본값 10)
    TimeWindow timeWindow,
    List<String> preferTags,  // 선호 태그 목록
    Boolean useProfileHints  // UserProfile 반영 여부 (기본값 true)
) {
    public record TimeWindow(
        LocalDate from,
        LocalDate to
    ) {}
    
    public Integer getTopKOrDefault() {
        return topK != null && topK > 0 ? topK : 10;
    }
    
    public Boolean getUseProfileHintsOrDefault() {
        return useProfileHints != null ? useProfileHints : true;
    }
}

