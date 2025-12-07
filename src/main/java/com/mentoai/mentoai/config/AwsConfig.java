package com.mentoai.mentoai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
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
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}


