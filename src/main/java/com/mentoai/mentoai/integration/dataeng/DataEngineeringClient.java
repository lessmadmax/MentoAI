package com.mentoai.mentoai.integration.dataeng;

import com.mentoai.mentoai.config.DataEngineeringProperties;
import com.mentoai.mentoai.controller.dto.JobRequirementPayload;
import com.mentoai.mentoai.entity.JobPostingEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataEngineeringClient {

    private final DataEngineeringProperties properties;

    @Qualifier("dataEngineeringRestTemplate")
    private final RestTemplate dataEngineeringRestTemplate;

    public JobRequirementPayload fetchJobRequirements(JobPostingEntity jobPosting) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            log.debug("Data engineering endpoint is disabled or not configured.");
            return JobRequirementPayload.empty();
        }

        try {
            JobRequirementRequest payload = new JobRequirementRequest(
                    jobPosting.getLink(),
                    jobPosting.getId(),
                    jobPosting.getCompanyName(),
                    jobPosting.getTitle()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<JobRequirementResponse> response = dataEngineeringRestTemplate.postForEntity(
                    properties.getUrl(),
                    new HttpEntity<>(payload, headers),
                    JobRequirementResponse.class
            );

            JobRequirementResponse body = response.getBody();
            if (body != null && body.payload() != null && !body.payload().isEmpty()) {
                return body.payload();
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch requirements from data engineering service for job {}: {}",
                    jobPosting.getId(), ex.getMessage());
        }

        return JobRequirementPayload.empty();
    }

    public record JobRequirementRequest(String jobUrl, Long jobId, String companyName, String title) {
    }

    public record JobRequirementResponse(JobRequirementPayload payload) {
    }
}

