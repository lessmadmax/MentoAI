package com.mentoai.mentoai.controller.dto;

import java.time.LocalDate;
import java.util.List;

public record RecommendRequest(
        Long userId,
        String query,  // 자연어 질의
        Integer topK,  // 추천 개수 (기본값 10)
        TimeWindow timeWindow,
        List<String> preferTags,  // 선호 태그 목록
        Boolean useProfileHints,  // UserProfile 반영 여부 (기본값 true)
        IntentHint intentHint     // 클라이언트가 추정한 의도 (선택)
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

    public record IntentHint(
            String normalizedIntent,    // 예: CONTEST, JOB, STUDY 등
            List<String> keywords,      // 추가 키워드
            ActivityFilter filter       // 의도 기반 필터링을 위한 정보
    ) {
    }

    public record ActivityFilter(
            String activityType,        // ActivityEntity.ActivityType 이름 (옵션)
            List<String> requiredTags   // 반드시 포함해야 할 태그 목록
    ) {}
}

