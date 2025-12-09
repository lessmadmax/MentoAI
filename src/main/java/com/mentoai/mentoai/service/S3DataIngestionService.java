package com.mentoai.mentoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoai.mentoai.config.AwsS3IngestionProperties;
import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.controller.dto.JobPostingUpsertRequest;
import com.mentoai.mentoai.controller.dto.RawActivityPayload;
import com.mentoai.mentoai.controller.dto.RawJobPostingPayload;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3DataIngestionService {

    @PersistenceContext
    private EntityManager entityManager;

    private final S3Client s3Client;
    private final AwsS3IngestionProperties properties;
    private final RawDataSchemaMapper rawDataSchemaMapper;
    private final ActivityService activityService;
    private final ActivityRepository activityRepository;
    private final JobPostingService jobPostingService;
    private final JobPostingRepository jobPostingRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public IngestionResult ingestContestActivities() {
        ensureEnabled();
        String prefix = properties.getContestPrefix();
        if (!StringUtils.hasText(prefix)) {
            return IngestionResult.disabled("contest");
        }
        return ingestActivitiesFromPrefix(prefix);
    }

    @Transactional
    public IngestionResult ingestJobPostings() {
        ensureEnabled();
        String prefix = properties.getJobPrefix();
        if (!StringUtils.hasText(prefix)) {
            return IngestionResult.disabled("job");
        }
        return ingestJobPostingsFromPrefix(prefix);
    }

    @Transactional
    public CombinedIngestionResult ingestAll() {
        IngestionResult contests = ingestContestActivities();
        IngestionResult jobs = ingestJobPostings();
        return new CombinedIngestionResult(contests, jobs);
    }

    private IngestionResult ingestActivitiesFromPrefix(String prefix) {
        Stats stats = new Stats("contest");
        for (S3Object object : listAllObjects(prefix)) {
            byte[] bytes = download(object.key());
            if (bytes.length == 0) {
                continue;
            }
            stats.incrementFiles();
            List<RawActivityPayload> payloads = deserialize(bytes, RawActivityPayload.class);
            for (RawActivityPayload raw : payloads) {
                stats.incrementPayloads();
                try {
                    ActivityUpsertRequest request = rawDataSchemaMapper.toActivityRequest(raw);
                    Optional<ActivityEntity> existing = findExistingActivity(raw);
                    if (existing.isPresent()) {
                        activityService.updateActivity(existing.get().getId(), request);
                        stats.incrementUpdated();
                    } else {
                        activityService.createActivity(request);
                        stats.incrementCreated();
                    }
                } catch (Exception e) {
                    stats.incrementFailed();
                    log.warn("Failed to ingest activity from key {}: {}", object.key(), e.getMessage());
                } finally {
                    // 한 payload 처리 후 영속성 컨텍스트를 비워 캐스케이드 잔존 엔티티로 인한 rollback-only를 방지
                    flushAndClear();
                }
            }
        }
        return stats.toResult();
    }

    private IngestionResult ingestJobPostingsFromPrefix(String prefix) {
        Stats stats = new Stats("job");
        for (S3Object object : listAllObjects(prefix)) {
            byte[] bytes = download(object.key());
            if (bytes.length == 0) {
                continue;
            }
            stats.incrementFiles();
            List<RawJobPostingPayload> payloads = deserialize(bytes, RawJobPostingPayload.class);
            for (RawJobPostingPayload raw : payloads) {
                stats.incrementPayloads();
                try {
                    JobPostingUpsertRequest request = rawDataSchemaMapper.toJobPostingRequest(raw);
                    Optional<JobPostingEntity> existing = findExistingJobPosting(raw);
                    if (existing.isPresent()) {
                        jobPostingService.updateJobPosting(existing.get().getId(), request);
                        stats.incrementUpdated();
                    } else {
                        jobPostingService.createJobPosting(request);
                        stats.incrementCreated();
                    }
                } catch (Exception e) {
                    stats.incrementFailed();
                    log.warn("Failed to ingest job posting from key {}: {}", object.key(), e.getMessage());
                } finally {
                    flushAndClear();
                }
            }
        }
        return stats.toResult();
    }

    private List<S3Object> listAllObjects(String prefix) {
        List<S3Object> objects = new ArrayList<>();
        String continuationToken = null;
        int maxKeys = properties.getMaxKeys() != null ? properties.getMaxKeys() : 200;
        do {
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(properties.getBucket())
                    .prefix(prefix)
                    .maxKeys(maxKeys);
            if (continuationToken != null) {
                builder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = s3Client.listObjectsV2(builder.build());
            objects.addAll(response.contents());
            continuationToken = response.nextContinuationToken();
            if (!response.isTruncated()) {
                break;
            }
        } while (true);
        return objects;
    }

    private byte[] download(String key) {
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to download S3 object {}: {}", key, e.getMessage());
            return new byte[0];
        }
    }

    private <T> List<T> deserialize(byte[] data, Class<T> type) {
        if (data.length == 0) {
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node.isArray()) {
                List<T> list = new ArrayList<>();
                for (JsonNode item : node) {
                    list.add(objectMapper.treeToValue(item, type));
                }
                return list;
            }
            return List.of(objectMapper.treeToValue(node, type));
        } catch (Exception ex) {
            return parseLineDelimited(data, type, ex);
        }
    }

    private <T> List<T> parseLineDelimited(byte[] data, Class<T> type, Exception original) {
        List<T> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(data), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                results.add(objectMapper.readValue(line, type));
            }
        } catch (IOException e) {
            log.warn("Failed to parse payload as {}: {}", type.getSimpleName(), original.getMessage());
            return Collections.emptyList();
        }
        return results;
    }

    private Optional<ActivityEntity> findExistingActivity(RawActivityPayload raw) {
        if (StringUtils.hasText(raw.url())) {
            Optional<ActivityEntity> byUrl = activityRepository.findFirstByUrl(raw.url());
            if (byUrl.isPresent()) {
                return byUrl;
            }
        }
        if (StringUtils.hasText(raw.title())) {
            return activityRepository.findFirstByTitleIgnoreCase(raw.title());
        }
        return Optional.empty();
    }

    private Optional<JobPostingEntity> findExistingJobPosting(RawJobPostingPayload raw) {
        if (StringUtils.hasText(raw.link())) {
            Optional<JobPostingEntity> byLink = jobPostingRepository.findFirstByLink(raw.link());
            if (byLink.isPresent()) {
                return byLink;
            }
        }
        if (StringUtils.hasText(raw.title())) {
            return jobPostingRepository.findFirstByTitleIgnoreCase(raw.title());
        }
        return Optional.empty();
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("S3 ingestion is disabled. Set aws.s3.ingest.enabled=true to use this feature.");
        }
        if (!StringUtils.hasText(properties.getBucket())) {
            throw new IllegalStateException("aws.s3.ingest.bucket is not configured.");
        }
    }

    private void flushAndClear() {
        try {
            entityManager.flush();
            entityManager.clear();
        } catch (Exception ignored) {
            // flush/clear 실패는 무시하고 다음 payload 처리
        }
    }

    private static final class Stats {
        private final String dataset;
        private int files;
        private int payloads;
        private int created;
        private int updated;
        private int failed;

        private Stats(String dataset) {
            this.dataset = dataset;
        }

        void incrementFiles() {
            files++;
        }

        void incrementPayloads() {
            payloads++;
        }

        void incrementCreated() {
            created++;
        }

        void incrementUpdated() {
            updated++;
        }

        void incrementFailed() {
            failed++;
        }

        IngestionResult toResult() {
            return new IngestionResult(dataset, files, payloads, created, updated, failed);
        }
    }

    public record IngestionResult(
            String dataset,
            int files,
            int payloads,
            int created,
            int updated,
            int failed
    ) {
        public static IngestionResult disabled(String dataset) {
            return new IngestionResult(dataset, 0, 0, 0, 0, 0);
        }
    }

    public record CombinedIngestionResult(
            IngestionResult contests,
            IngestionResult jobPostings
    ) {
    }
}


