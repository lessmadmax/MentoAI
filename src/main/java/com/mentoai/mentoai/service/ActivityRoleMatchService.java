package com.mentoai.mentoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.ActivityTargetRoleEntity;
import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.entity.WeightedMajor;
import com.mentoai.mentoai.entity.WeightedSkill;
import com.mentoai.mentoai.integration.qdrant.ActivityVectorPayload;
import com.mentoai.mentoai.integration.qdrant.QdrantClient;
import com.mentoai.mentoai.integration.qdrant.QdrantSearchResult;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.ActivityTargetRoleRepository;
import com.mentoai.mentoai.repository.TargetRoleRepository;
import com.mentoai.mentoai.controller.dto.RecommendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRoleMatchService {

    private final GeminiService geminiService;
    private final QdrantClient qdrantClient;
    private final com.mentoai.mentoai.config.QdrantProperties qdrantProperties;
    private final ActivityTargetRoleRepository activityTargetRoleRepository;
    private final TargetRoleRepository targetRoleRepository;
    private final ActivityRepository activityRepository;
    private final ObjectMapper objectMapper;
    private final UserProfileService userProfileService;

    public record RoleMatch(Long activityId, double score, Map<String, Object> payload) {
    }

    /**
     * 활동 임베딩을 생성해 Qdrant에 업서트합니다.
     */
    @Transactional(readOnly = true)
    public void indexActivity(ActivityEntity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }

        String document = buildActivityDocument(activity);
        if (document.isBlank()) {
            log.debug("Skip indexing activity {} because document text is empty", activity.getId());
            return;
        }

        try {
            List<Double> embedding = geminiService.generateEmbedding(document);
            Map<String, Object> payload = new HashMap<>();
            payload.put("activityId", activity.getId());
            payload.put("title", activity.getTitle());
            payload.put("type", activity.getType() != null ? activity.getType().name() : null);
            payload.put("campus", Boolean.TRUE.equals(activity.getIsCampus()));

            ActivityVectorPayload vectorPayload = new ActivityVectorPayload(
                    String.valueOf(activity.getId()),
                    embedding,
                    payload
            );
            qdrantClient.upsertActivityVectors(List.of(vectorPayload));
        } catch (Exception e) {
            log.warn("Failed to index activity {} into Qdrant: {}", activity.getId(), e.getMessage());
        }
    }

    /**
     * 활동 삭제 시 Qdrant 포인트와 매핑 데이터를 정리합니다.
     */
    @Transactional
    public void deleteActivityVector(Long activityId) {
        if (activityId == null) {
            return;
        }
        try {
            qdrantClient.deletePoint(String.valueOf(activityId));
        } catch (Exception e) {
            log.warn("Failed to delete Qdrant vector for activity {}: {}", activityId, e.getMessage());
        }
        activityTargetRoleRepository.deleteByActivityId(activityId);
    }

    /**
     * targetRoleId에 대한 Qdrant 검색을 수행하고 결과를 activity_target_roles 테이블에 반영합니다.
     */
    @Transactional
    public List<ActivityTargetRoleEntity> refreshRoleMatches(String targetRoleId, int topK) {
        TargetRoleEntity role = targetRoleRepository.findById(targetRoleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 targetRoleId: " + targetRoleId));

        List<QdrantSearchResult> results = performRoleSearch(role, topK);
        if (results.isEmpty()) {
            return List.of();
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<ActivityTargetRoleEntity> saved = new ArrayList<>();

        for (QdrantSearchResult result : results) {
            Long activityId = extractActivityId(result);
            if (activityId == null) {
                continue;
            }

            Optional<ActivityEntity> activityOpt = activityRepository.findById(activityId);
            if (activityOpt.isEmpty()) {
                continue;
            }

            ActivityTargetRoleEntity mapping = activityTargetRoleRepository
                    .findByActivityIdAndTargetRoleId(activityId, targetRoleId)
                    .orElseGet(ActivityTargetRoleEntity::new);

            mapping.setActivity(activityOpt.get());
            mapping.setTargetRole(role);
            mapping.setSimilarityScore(result.score());
            mapping.setLastSyncedAt(now);
            mapping.setSyncStatus(ActivityTargetRoleEntity.SyncStatus.SYNCED);

            copyPayloadIfPresent(result.payload(), "matchedRequirements", mapping::setMatchedRequirements);
            copyPayloadIfPresent(result.payload(), "matchedPreferences", mapping::setMatchedPreferences);

            saved.add(activityTargetRoleRepository.save(mapping));
        }

        return saved;
    }

    /**
     * targetRoleId 기준으로 Qdrant 검색만 수행하고 활동 ID 목록을 반환합니다.
     */
    @Transactional(readOnly = true)
    public List<Long> searchActivityIdsForRole(String targetRoleId, int topK) {
        return findRoleMatches(targetRoleId, topK).stream()
                .map(RoleMatch::activityId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleMatch> findRoleMatches(String targetRoleId, int topK) {
        List<QdrantSearchResult> results = performRoleSearch(targetRoleId, topK);
        if (results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .map(result -> new RoleMatch(extractActivityId(result), result.score(), result.payload()))
                .filter(match -> match.activityId() != null)
                .toList();
    }

    private List<QdrantSearchResult> performRoleSearch(String targetRoleId, int topK) {
        TargetRoleEntity role = targetRoleRepository.findById(targetRoleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 targetRoleId: " + targetRoleId));
        return performRoleSearch(role, topK);
    }

    private List<QdrantSearchResult> performRoleSearch(TargetRoleEntity role, int topK) {
        if (role == null) {
            return List.of();
        }

        String roleDocument = buildRoleDocument(role);
        if (roleDocument.isBlank()) {
            log.warn("Target role {} has insufficient data to build embedding", role.getRoleId());
            return List.of();
        }

        int safeTopK = clampTopK(topK);
        try {
            List<Double> roleEmbedding = geminiService.generateEmbedding(roleDocument);
            return qdrantClient.searchAcrossCollections(
                    roleEmbedding,
                    safeTopK,
                    null,
                    qdrantProperties.activityCollections()
            );
        } catch (Exception e) {
            log.warn("Failed to retrieve Qdrant matches for role {}: {}", role.getRoleId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 사용자 프로필 임베딩을 이용해 활동 벡터를 검색합니다.
     */
    @Transactional(readOnly = true)
    public List<RoleMatch> findMatchesForUserProfile(Long userId, int topK) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        String profileDoc = buildUserProfileDocument(profile);
        if (profileDoc.isBlank()) {
            log.warn("User {} has insufficient profile data to build embedding", userId);
            return List.of();
        }

        int safeTopK = clampTopK(topK);
        try {
            List<Double> embedding = geminiService.generateEmbedding(profileDoc);
            List<QdrantSearchResult> results = qdrantClient.searchAcrossCollections(
                    embedding,
                    safeTopK,
                    null,
                    qdrantProperties.activityCollections()
            );
            return results.stream()
                    .map(result -> new RoleMatch(extractActivityId(result), result.score(), result.payload()))
                    .filter(match -> match.activityId() != null)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to retrieve Qdrant matches for user {} profile: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * targetRole, user profile, user query를 하나의 문서로 결합하여 임베딩 검색.
     * 가중치 없이 단순 결합.
     */
    @Transactional(readOnly = true)
    public List<QdrantSearchResult> hybridSearch(
            String targetRoleId,
            Long userId,
            String userQuery,
            int topK
    ) {
        StringBuilder doc = new StringBuilder();

        // target role 문서
        if (targetRoleId != null && !targetRoleId.isBlank()) {
            targetRoleRepository.findById(targetRoleId).ifPresent(role -> {
                doc.append("ROLE: ").append(buildRoleDocument(role)).append(" ");
            });
        }

        // user profile 문서
        if (userId != null) {
            try {
                UserProfileResponse profile = userProfileService.getProfile(userId);
                String profileDoc = buildUserProfileDocument(profile);
                if (!profileDoc.isBlank()) {
                    doc.append("PROFILE: ").append(profileDoc).append(" ");
                }
            } catch (Exception ignored) {
                // 프로필이 없으면 무시
            }
        }

        // 사용자 쿼리
        if (userQuery != null && !userQuery.isBlank()) {
            doc.append("QUERY: ").append(userQuery.trim());
        }

        String hybridDoc = doc.toString().trim();
        if (hybridDoc.isEmpty()) {
            log.warn("Hybrid search skipped because document is empty (role={}, userId={}, query='{}')",
                    targetRoleId, userId, userQuery);
            return List.of();
        }

        int safeTopK = clampTopK(topK);
        try {
            List<Double> embedding = geminiService.generateEmbedding(hybridDoc);
            return qdrantClient.searchAcrossCollections(
                    embedding,
                    safeTopK,
                    null,
                    qdrantProperties.activityCollections()
            );
        } catch (Exception e) {
            log.warn("Hybrid search failed (role={}, userId={}, query='{}'): {}",
                    targetRoleId, userId, userQuery, e.getMessage());
            return List.of();
        }
    }

    private int clampTopK(int topK) {
        return Math.max(1, Math.min(topK, 200));
    }

    private void copyPayloadIfPresent(Map<String, Object> payload, String key, java.util.function.Consumer<String> consumer) {
        if (payload == null || consumer == null) {
            return;
        }
        Object value = payload.get(key);
        if (value == null) {
            return;
        }
        if (value instanceof String str) {
            consumer.accept(str);
        } else {
            consumer.accept(writeAsJson(value));
        }
    }

    private String writeAsJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    public Long extractActivityId(QdrantSearchResult result) {
        if (result == null || result.payload() == null) {
            return null;
        }
        Object raw = result.payload().getOrDefault("activityId", result.payload().get("activity_id"));
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

    private String buildActivityDocument(ActivityEntity activity) {
        StringBuilder builder = new StringBuilder();

        appendIfPresent(builder, activity.getTitle());
        appendIfPresent(builder, activity.getSummary());

        if (activity.getContent() != null) {
            String content = activity.getContent();
            if (content.length() > 600) {
                content = content.substring(0, 600);
            }
            appendIfPresent(builder, content);
        }

        if (!CollectionUtils.isEmpty(activity.getActivityTags())) {
            List<String> tagNames = activity.getActivityTags().stream()
                    .map(ActivityTagEntity::getTag)
                    .filter(Objects::nonNull)
                    .map(tag -> tag.getName())
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!tagNames.isEmpty()) {
                builder.append("Tags: ").append(String.join(", ", tagNames)).append(". ");
            }
        }

        if (activity.getType() != null) {
            builder.append("Type: ").append(activity.getType().name()).append(". ");
        }
        if (activity.getOrganizer() != null) {
            builder.append("Organizer: ").append(activity.getOrganizer()).append(". ");
        }
        return builder.toString().trim();
    }

    private String buildRoleDocument(TargetRoleEntity role) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, role.getName());
        appendIfPresent(builder, role.getExpectedSeniority());

        appendWeightedList(builder, "Required skills", role.getRequiredSkills());
        appendWeightedList(builder, "Bonus skills", role.getBonusSkills());
        appendWeightedMajors(builder, role.getMajorMapping());

        if (!CollectionUtils.isEmpty(role.getRecommendedCerts())) {
            builder.append("Recommended certifications: ").append(String.join(", ", role.getRecommendedCerts())).append(". ");
        }

        return builder.toString().trim();
    }

    private String buildUserProfileDocument(UserProfileResponse profile) {
        if (profile == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();

        appendIfPresent(builder, profile.targetRoleId());

        UserProfileResponse.University uni = profile.university();
        if (uni != null) {
            appendIfPresent(builder, uni.universityName());
            appendIfPresent(builder, uni.major());
            if (uni.grade() != null) {
                builder.append("Grade: ").append(uni.grade()).append(". ");
            }
        }

        appendStringList(builder, "Interests", profile.interestDomains());

        if (!CollectionUtils.isEmpty(profile.certifications())) {
            List<String> certNames = profile.certifications().stream()
                    .filter(Objects::nonNull)
                    .map(UserProfileResponse.Certification::name)
                    .filter(Objects::nonNull)
                    .toList();
            appendStringList(builder, "Certifications", certNames);
        }

        if (!CollectionUtils.isEmpty(profile.techStack())) {
            List<String> skills = profile.techStack().stream()
                    .filter(Objects::nonNull)
                    .map(skill -> skill.level() != null
                            ? skill.name() + " (" + skill.level() + ")"
                            : skill.name())
                    .filter(Objects::nonNull)
                    .toList();
            appendStringList(builder, "Skills", skills);
        }

        if (!CollectionUtils.isEmpty(profile.experiences())) {
            for (UserProfileResponse.Experience exp : profile.experiences()) {
                if (exp == null) continue;
                StringBuilder expBuilder = new StringBuilder();
                appendIfPresent(expBuilder, exp.title());
                appendIfPresent(expBuilder, exp.organization());
                appendIfPresent(expBuilder, exp.role());
                appendIfPresent(expBuilder, exp.description());
                if (exp.techStack() != null && !exp.techStack().isEmpty()) {
                    expBuilder.append("TechStack: ").append(String.join(", ", exp.techStack())).append(". ");
                }
                if (expBuilder.length() > 0) {
                    builder.append("Experience: ").append(expBuilder);
                }
            }
        }

        return builder.toString().trim();
    }

    private void appendIfPresent(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            builder.append(text.trim()).append(". ");
        }
    }

    private void appendWeightedList(StringBuilder builder, String label, List<WeightedSkill> skills) {
        if (CollectionUtils.isEmpty(skills)) {
            return;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (WeightedSkill skill : skills) {
            if (skill == null || skill.getName() == null) {
                continue;
            }
            if (skill.getWeight() != null) {
                joiner.add(skill.getName() + " (" + skill.getWeight() + ")");
            } else {
                joiner.add(skill.getName());
            }
        }
        if (joiner.length() > 0) {
            builder.append(label).append(": ").append(joiner).append(". ");
        }
    }

    private void appendStringList(StringBuilder builder, String label, List<String> items) {
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        List<String> cleaned = items.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (!cleaned.isEmpty()) {
            builder.append(label).append(": ").append(String.join(", ", cleaned)).append(". ");
        }
    }

    private void appendWeightedMajors(StringBuilder builder, List<WeightedMajor> majors) {
        if (CollectionUtils.isEmpty(majors)) {
            return;
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (WeightedMajor major : majors) {
            if (major == null || major.getMajor() == null) {
                continue;
            }
            if (major.getWeight() != null) {
                joiner.add(major.getMajor() + " (" + major.getWeight() + ")");
            } else {
                joiner.add(major.getMajor());
            }
        }
        if (joiner.length() > 0) {
            builder.append("Majors: ").append(joiner).append(". ");
        }
    }
}

