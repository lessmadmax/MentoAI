package com.mentoai.mentoai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dataeng.requirements")
public class DataEngineeringProperties {

    /**
     * 데이터 엔지니어링 서비스의 공고 분석 API URL.
     */
    private String url;

    /**
     * 요청 타임아웃(ms).
     */
    private Integer timeoutMs = 5000;

    /**
     * 기능 활성화 여부.
     */
    private boolean enabled = true;
}

