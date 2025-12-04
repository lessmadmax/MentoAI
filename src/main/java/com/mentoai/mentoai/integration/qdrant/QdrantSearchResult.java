package com.mentoai.mentoai.integration.qdrant;

import java.util.Map;

/**
 * Qdrant 검색 결과 요약.
 *
 * @param pointId 포인트 ID
 * @param score   유사도 점수
 * @param payload 저장된 메타데이터
 */
public record QdrantSearchResult(
        String pointId,
        double score,
        Map<String, Object> payload
) {
}


