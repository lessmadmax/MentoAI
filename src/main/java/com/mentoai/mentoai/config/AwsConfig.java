package com.mentoai.mentoai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AwsS3IngestionProperties.class)
public class AwsConfig {

    @Bean
    public S3Client s3Client(AwsS3IngestionProperties properties) {
        String region = StringUtils.hasText(properties.getRegion())
                ? properties.getRegion()
                : Region.AP_NORTHEAST_2.id();
        var builder = S3Client.builder().region(Region.of(region));

        // 자격증명 우선순위: 표준 AWS_* → AWS_ACCESS_KEY/SECRET_KEY → 커스텀 IAM_* → 기본 체인
        String accessKey = firstNonBlank(
                System.getenv("AWS_ACCESS_KEY_ID"),
                System.getenv("AWS_ACCESS_KEY"),
                System.getenv("IAM_ACCESS_KEY")
        );
        String secretKey = firstNonBlank(
                System.getenv("AWS_SECRET_ACCESS_KEY"),
                System.getenv("AWS_SECRET_KEY"),
                System.getenv("IAM_SECRET_KEY")
        );
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)
                    )
            );
        }

        return builder.build();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}


