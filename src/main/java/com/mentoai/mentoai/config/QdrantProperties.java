package com.mentoai.mentoai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    /**
     * Qdrant base URL (예: https://...cloud.qdrant.io).
     */
    private String url;

    /**
     * Qdrant API Key (필요 시).
     */
    private String apiKey;

    /**
     * 활동 기본 컬렉션 이름.
     */
    private String collection;

    /**
     * 링크커리어 컨테스트 컬렉션.
     */
    private String linkareerContest;

    /**
     * 링크커리어 공모 컬렉션.
     */
    private String linkareerGongmo;

    /**
     * 사용자 프로필 컬렉션.
     */
    private String userProfiles;

    /**
     * 활동 벡터 차원 수 (기본 768).
     */
    private Integer vectorDim = 768;

    /**
     * 채용 공고 기본 컬렉션 이름.
     */
    private String jobCollection;

    /**
     * 잡다 채용/상세 컬렉션.
     */
    private String jobdaRecruit;
    private String jobdaDetail;

    /**
     * 채용 공고 벡터 차원 수 (지정되지 않으면 기본 차원 사용).
     */
    private Integer jobVectorDim;

    /**
     * REST 호출 타임아웃(ms).
     */
    private Integer timeoutMs = 5000;

    public String resolvedActivityCollection() {
        return firstNonBlank(collection, linkareerContest, linkareerGongmo);
    }

    public String resolvedJobCollection() {
        return firstNonBlank(jobCollection, jobdaRecruit, jobdaDetail, collection);
    }

    public java.util.List<String> activityCollections() {
        return nonBlankList(collection, linkareerContest, linkareerGongmo);
    }

    public java.util.List<String> jobCollections() {
        return nonBlankList(jobCollection, jobdaRecruit, jobdaDetail, collection);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private java.util.List<String> nonBlankList(String... values) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (values != null) {
            for (String v : values) {
                if (v != null && !v.isBlank()) {
                    list.add(v);
                }
            }
        }
        return list;
    }
}


