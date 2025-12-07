package com.mentoai.mentoai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "aws.s3.ingest")
public class AwsS3IngestionProperties {

    /**
     * 기능 on/off 토글.
     */
    private boolean enabled;

    /**
     * S3 리전 (예: ap-northeast-2).
     */
    private String region;

    /**
     * 데이터가 저장된 버킷명.
     */
    private String bucket;

    /**
     * 공모전/대회 데이터가 위치한 프리픽스.
     */
    private String contestPrefix;

    /**
     * 채용 공고 데이터가 위치한 프리픽스.
     */
    private String jobPrefix;

    /**
     * ListObjects 호출 시 한 번에 가져올 최대 객체 수.
     */
    private Integer maxKeys = 200;
}


