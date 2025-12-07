package com.mentoai.mentoai.integration.qdrant;

import com.mentoai.mentoai.config.QdrantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Qdrant REST API thin-client.
 * 향후 Recommend/Activity 서비스에서 해당 컴포넌트를 주입받아 사용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantClient {

    private final RestTemplate qdrantRestTemplate;
    private final QdrantProperties properties;

    /**
     * 활동 임베딩을 Qdrant 컬렉션에 업서트합니다.
     */
    public void upsertActivityVectors(List<ActivityVectorPayload> payloads) {
        upsertVectors(payloads, properties.getCollection(), properties.getVectorDim());
    }

    public void upsertVectors(List<ActivityVectorPayload> payloads,
                              String collection,
                              Integer expectedDim) {
        if (CollectionUtils.isEmpty(payloads)) {
            return;
        }

        String resolvedCollection = resolveCollection(collection);
        ensureCollectionConfigured(resolvedCollection);

        String endpoint = collectionUrl(resolvedCollection, "/points?wait=true");
        Map<String, Object> body = Map.of(
                "points", payloads.stream()
                        .map(payload -> toPointRequest(payload, expectedDim))
                        .collect(Collectors.toList())
        );

        execute(endpoint, HttpMethod.PUT, body);
    }

    /**
     * 주어진 벡터 임베딩으로 Qdrant에서 유사한 활동을 검색합니다.
     */
    public List<QdrantSearchResult> searchByEmbedding(
            List<Double> embedding,
            int topK,
            Map<String, Object> filter
    ) {
        return searchByEmbedding(embedding, topK, filter, properties.getCollection());
    }

    public List<QdrantSearchResult> searchByEmbedding(
            List<Double> embedding,
            int topK,
            Map<String, Object> filter,
            String collection
    ) {
        String resolvedCollection = resolveCollection(collection);
        ensureCollectionConfigured(resolvedCollection);

        String endpoint = collectionUrl(resolvedCollection, "/points/search");
        Map<String, Object> body = new HashMap<>();
        body.put("vector", embedding);
        body.put("top", topK);
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
        }

        ResponseEntity<QdrantSearchResponse> response = exchange(
                endpoint,
                HttpMethod.POST,
                body,
                QdrantSearchResponse.class
        );

        if (response == null || response.getBody() == null || response.getBody().result() == null) {
            return List.of();
        }

        return response.getBody().result().stream()
                .map(res -> new QdrantSearchResult(
                        res.id() != null ? res.id().toString() : null,
                        res.score() != null ? res.score() : 0.0,
                        res.payload() != null ? res.payload() : Collections.emptyMap()
                ))
                .toList();
    }

    /**
     * 포인트 ID로 Qdrant 데이터 삭제.
     */
    public void deletePoint(String pointId) {
        deletePoint(pointId, properties.getCollection());
    }

    public void deletePoint(String pointId, String collection) {
        if (pointId == null || pointId.isBlank()) {
            return;
        }
        String resolvedCollection = resolveCollection(collection);
        ensureCollectionConfigured(resolvedCollection);

        String endpoint = collectionUrl(resolvedCollection, "/points/delete?wait=true");
        Map<String, Object> body = Map.of("points", List.of(pointId));
        execute(endpoint, HttpMethod.POST, body);
    }

    private void execute(String endpoint, HttpMethod method, Object body) {
        try {
            exchange(endpoint, method, body, Map.class);
        } catch (RestClientException e) {
            log.error("Qdrant request failed: {} {} - {}", method, endpoint, e.getMessage());
            throw e;
        }
    }

    private <T> ResponseEntity<T> exchange(String endpoint,
                                           HttpMethod method,
                                           Object body,
                                           Class<T> responseType) {
        HttpHeaders headers = defaultHeaders();
        HttpEntity<Object> requestEntity = new HttpEntity<>(body, headers);
        try {
            return qdrantRestTemplate.exchange(endpoint, method, requestEntity, responseType);
        } catch (RestClientException e) {
            log.error("Qdrant request failed: {} {} - {}", method, endpoint, e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> toPointRequest(ActivityVectorPayload payload, Integer expectedDim) {
        if (expectedDim != null && payload.vector() != null && payload.vector().size() != expectedDim) {
            log.warn("Vector dimension mismatch for point {}. expected={}, actual={}",
                    payload.pointId(), expectedDim, payload.vector().size());
        }
        Map<String, Object> point = new HashMap<>();
        point.put("id", payload.pointId());
        point.put("vector", payload.vector());
        if (payload.payload() != null) {
            point.put("payload", payload.payload());
        }
        return point;
    }

    private HttpHeaders defaultHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.setBearerAuth(properties.getApiKey());
        }
        return headers;
    }

    private String collectionUrl(String collection, String suffix) {
        String base = Objects.requireNonNull(properties.getUrl(), "qdrant.url is required").replaceAll("/$", "");
        return base + "/collections/" + collection + suffix;
    }

    private void ensureCollectionConfigured(String collection) {
        Objects.requireNonNull(properties.getUrl(), "qdrant.url is required");
        Objects.requireNonNull(collection, "qdrant.collection is required");
    }

    private String resolveCollection(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        return Objects.requireNonNull(properties.getCollection(), "qdrant.collection is required");
    }

    /**
     * Qdrant Search API 응답 DTO.
     */
    private record QdrantSearchResponse(
            List<QdrantPointResult> result
    ) {
    }

    private record QdrantPointResult(
            Object id,
            Double score,
            Map<String, Object> payload
    ) {
    }
}

