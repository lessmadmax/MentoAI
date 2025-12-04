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
     * 사용할 컬렉션 이름.
     */
    private String collection;

    /**
     * 벡터 차원 수 (기본 768).
     */
    private Integer vectorDim = 768;

    /**
     * REST 호출 타임아웃(ms).
     */
    private Integer timeoutMs = 5000;
}


