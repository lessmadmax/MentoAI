package com.mentoai.mentoai.integration.qdrant;

import java.util.List;
import java.util.Map;

/**
 * Qdrant에 업서트할 포인트 1건을 표현합니다.
 *
 * @param pointId 고유 포인트 ID (예: activity-123)
 * @param vector  임베딩 벡터 값
 * @param payload 추가 메타데이터(활동 ID, 직무 등)
 */
public record ActivityVectorPayload(
        String pointId,
        List<Double> vector,
        Map<String, Object> payload
) {
}


