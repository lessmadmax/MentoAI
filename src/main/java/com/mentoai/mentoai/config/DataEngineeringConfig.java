package com.mentoai.mentoai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(DataEngineeringProperties.class)
public class DataEngineeringConfig {

    @Bean
    public RestTemplate dataEngineeringRestTemplate(DataEngineeringProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = properties.getTimeoutMs() != null ? properties.getTimeoutMs() : 5000;
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(requestFactory);
        return restTemplate;
    }
}

