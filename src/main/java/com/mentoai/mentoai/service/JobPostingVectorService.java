package com.mentoai.mentoai.service;

import com.mentoai.mentoai.config.QdrantProperties;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.entity.JobPostingRoleEntity;
import com.mentoai.mentoai.entity.JobPostingSkillEntity;
import com.mentoai.mentoai.integration.qdrant.ActivityVectorPayload;
import com.mentoai.mentoai.integration.qdrant.QdrantClient;
import com.mentoai.mentoai.integration.qdrant.QdrantSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingVectorService {

    private static final int DESCRIPTION_LIMIT = 700;
    private static final int REQUIREMENT_LIMIT = 900;
    private static final int BENEFIT_LIMIT = 500;

    private final GeminiService geminiService;
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    public boolean isVectorSearchEnabled() {
        return jobCollectionName() != null;
    }

    public void indexJobPosting(JobPostingEntity jobPosting) {
        if (jobPosting == null || jobPosting.getId() == null) {
            return;
        }
        String collection = jobCollectionName();
        if (collection == null) {
            log.debug("Skip job posting vector index because qdrant.job-collection is not configured");
            return;
        }

        String document = buildDocument(jobPosting);
        if (!StringUtils.hasText(document)) {
            log.debug("Skip indexing job posting {} because document is empty", jobPosting.getId());
            return;
        }

        try {
            List<Double> embedding = geminiService.generateEmbedding(document);
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobPostingId", jobPosting.getId());
            payload.put("title", jobPosting.getTitle());
            payload.put("companyName", jobPosting.getCompanyName());
            payload.put("jobSector", jobPosting.getJobSector());
            payload.put("targetRoles", extractTargetRoleIds(jobPosting));

            ActivityVectorPayload vectorPayload = new ActivityVectorPayload(
                    "job-" + jobPosting.getId(),
                    embedding,
                    payload
            );

            qdrantClient.upsertVectors(
                    List.of(vectorPayload),
                    collection,
                    jobVectorDimension()
            );
        } catch (Exception e) {
            log.warn("Failed to index job posting {} into Qdrant: {}", jobPosting.getId(), e.getMessage());
        }
    }

    public List<QdrantSearchResult> search(List<Double> embedding, int topK) {
        List<String> collections = qdrantProperties.jobCollections();
        if (collections.isEmpty() || CollectionUtils.isEmpty(embedding)) {
            return List.of();
        }

        int safeTopK = Math.max(1, Math.min(topK, 500));
        try {
            return qdrantClient.searchAcrossCollections(embedding, safeTopK, null, collections);
        } catch (Exception e) {
            log.warn("Job posting vector search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteJobPostingVector(Long jobPostingId) {
        String collection = jobCollectionName();
        if (collection == null || jobPostingId == null) {
            return;
        }
        try {
            qdrantClient.deletePoint("job-" + jobPostingId, collection);
        } catch (Exception e) {
            log.warn("Failed to delete job posting vector {}: {}", jobPostingId, e.getMessage());
        }
    }

    public Long extractJobPostingId(QdrantSearchResult result) {
        if (result == null || result.payload() == null) {
            return null;
        }
        Object raw = result.payload().getOrDefault("jobPostingId", result.payload().get("job_posting_id"));
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> extractTargetRoleIds(JobPostingEntity jobPosting) {
        if (jobPosting.getTargetRoles() == null) {
            return Collections.emptyList();
        }
        return jobPosting.getTargetRoles().stream()
                .map(JobPostingRoleEntity::getTargetRole)
                .filter(Objects::nonNull)
                .map(role -> role.getRoleId())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String buildDocument(JobPostingEntity jobPosting) {
        StringBuilder builder = new StringBuilder();
        append(builder, jobPosting.getCompanyName());
        append(builder, jobPosting.getTitle());
        append(builder, jobPosting.getJobSector());
        append(builder, jobPosting.getEmploymentType());
        append(builder, jobPosting.getWorkPlace());
        append(builder, jobPosting.getCareerLevel());
        append(builder, jobPosting.getEducationLevel());
        append(builder, truncate(jobPosting.getDescription(), DESCRIPTION_LIMIT));
        append(builder, truncate(jobPosting.getRequirements(), REQUIREMENT_LIMIT));
        append(builder, truncate(jobPosting.getBenefits(), BENEFIT_LIMIT));

        if (!CollectionUtils.isEmpty(jobPosting.getSkills())) {
            builder.append("Required skills: ");
            builder.append(jobPosting.getSkills().stream()
                    .map(JobPostingSkillEntity::getSkillName)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.joining(", ")));
            builder.append(". ");
        }

        if (!CollectionUtils.isEmpty(jobPosting.getTargetRoles())) {
            builder.append("Target roles: ");
            builder.append(jobPosting.getTargetRoles().stream()
                    .map(JobPostingRoleEntity::getTargetRole)
                    .filter(Objects::nonNull)
                    .map(role -> role.getName())
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.joining(", ")));
            builder.append(". ");
        }

        return builder.toString().trim();
    }

    private void append(StringBuilder builder, String text) {
        if (StringUtils.hasText(text)) {
            builder.append(text.trim()).append(". ");
        }
    }

    private String truncate(String text, int max) {
        if (!StringUtils.hasText(text) || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private String jobCollectionName() {
        return qdrantProperties.resolvedJobCollection();
    }

    private Integer jobVectorDimension() {
        if (qdrantProperties.getJobVectorDim() != null) {
            return qdrantProperties.getJobVectorDim();
        }
        return qdrantProperties.getVectorDim();
    }
}


