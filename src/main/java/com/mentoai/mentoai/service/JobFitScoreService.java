package com.mentoai.mentoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoai.mentoai.controller.dto.ImprovementItem;
import com.mentoai.mentoai.controller.dto.JobFitScoreResponse;
import com.mentoai.mentoai.controller.dto.JobRequirementPayload;
import com.mentoai.mentoai.controller.dto.RoleFitResponse;
import com.mentoai.mentoai.entity.JobFitScoreEntity;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.entity.WeightedMajor;
import com.mentoai.mentoai.entity.WeightedSkill;
import com.mentoai.mentoai.integration.dataeng.DataEngineeringClient;
import com.mentoai.mentoai.repository.JobFitScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class JobFitScoreService {

    private static final int IMPROVEMENT_DEFAULT = 5;
    private static final TypeReference<List<RoleFitResponse.MissingSkill>> MISSING_SKILL_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ImprovementItem>> IMPROVEMENT_TYPE =
            new TypeReference<>() {};

    private final JobPostingService jobPostingService;
    private final JobFitScoreRepository jobFitScoreRepository;
    private final DataEngineeringClient dataEngineeringClient;
    private final RoleFitService roleFitService;
    private final ObjectMapper objectMapper;

    public JobFitScoreResponse evaluateJobFit(Long userId, Long jobId) {
        JobPostingEntity jobPosting = jobPostingService.getJobPosting(jobId)
                .orElseThrow(() -> new IllegalArgumentException("채용 공고를 찾을 수 없습니다: " + jobId));

        JobRequirementPayload requirements = dataEngineeringClient.fetchJobRequirements(jobPosting);
        if (requirements == null || requirements.isEmpty()) {
            requirements = fallbackRequirements(jobPosting);
        }

        TargetRoleEntity dynamicTarget = buildDynamicTarget(jobPosting, requirements);
        String targetLabel = "%s (%s)".formatted(
                jobPosting.getTitle(),
                StringUtils.hasText(jobPosting.getCompanyName()) ? jobPosting.getCompanyName() : ""
        ).trim();

        RoleFitResponse roleFit = roleFitService.calculateRoleFitAgainstTarget(
                userId,
                dynamicTarget,
                targetLabel.isBlank() ? "job-" + jobPosting.getId() : targetLabel
        );

        List<ImprovementItem> improvements = roleFitService.recommendImprovements(
                userId,
                dynamicTarget.getRoleId(),
                IMPROVEMENT_DEFAULT
        );

        JobFitScoreEntity entity = jobFitScoreRepository.findByUserIdAndJobId(userId, jobId)
                .orElseGet(JobFitScoreEntity::new);

        entity.setUserId(userId);
        entity.setJobId(jobId);
        entity.setTargetRoleId(roleFit.target());
        entity.setJobUrl(jobPosting.getLink());
        entity.setTotalScore(roleFit.roleFitScore());
        entity.setSkillFit(roleFit.breakdown().skillFit());
        entity.setExperienceFit(roleFit.breakdown().experienceFit());
        entity.setEducationFit(roleFit.breakdown().educationFit());
        entity.setEvidenceFit(roleFit.breakdown().evidenceFit());
        entity.setMissingSkillsJson(writeJson(roleFit.missingSkills()));
        entity.setRecommendationsJson(writeJson(roleFit.recommendations()));
        entity.setImprovementsJson(writeJson(improvements));
        entity.setRequirementsJson(writeJson(requirements));

        JobFitScoreEntity saved = jobFitScoreRepository.save(entity);
        return toResponse(saved, jobPosting, roleFit.missingSkills(), roleFit.recommendations(), improvements, requirements, false);
    }

    @Transactional(readOnly = true)
    public Optional<JobFitScoreResponse> getLatestScore(Long userId, Long jobId) {
        Optional<JobFitScoreEntity> optional = jobFitScoreRepository.findByUserIdAndJobId(userId, jobId);
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        JobPostingEntity jobPosting = jobPostingService.getJobPosting(jobId)
                .orElseThrow(() -> new IllegalArgumentException("채용 공고를 찾을 수 없습니다: " + jobId));

        JobFitScoreEntity entity = optional.get();
        List<RoleFitResponse.MissingSkill> missingSkills = readJson(
                entity.getMissingSkillsJson(),
                MISSING_SKILL_TYPE,
                Collections.emptyList()
        );
        List<String> recommendations = readJson(
                entity.getRecommendationsJson(),
                STRING_LIST_TYPE,
                Collections.emptyList()
        );
        List<ImprovementItem> improvements = readJson(
                entity.getImprovementsJson(),
                IMPROVEMENT_TYPE,
                Collections.emptyList()
        );
        JobRequirementPayload requirements = readRequirements(entity.getRequirementsJson());

        return Optional.of(
                toResponse(entity, jobPosting, missingSkills, recommendations, improvements, requirements, true)
        );
    }

    private JobRequirementPayload fallbackRequirements(JobPostingEntity jobPosting) {
        List<String> required = new ArrayList<>(extractLines(jobPosting.getRequirements()));
        List<String> preferred = new ArrayList<>(extractLines(jobPosting.getBenefits()));
        List<String> requiredMajors = new ArrayList<>();
        List<String> preferredMajors = new ArrayList<>();

        extractMajorEntries(required, requiredMajors);
        extractMajorEntries(preferred, preferredMajors);

        if (StringUtils.hasText(jobPosting.getEducationLevel())) {
            requiredMajors.add(jobPosting.getEducationLevel().trim());
        }

        String expectedSeniority = StringUtils.hasText(jobPosting.getCareerLevel())
                ? jobPosting.getCareerLevel()
                : null;

        return new JobRequirementPayload(
                required,
                preferred,
                Collections.emptyList(),
                Collections.emptyList(),
                deduplicate(requiredMajors),
                deduplicate(preferredMajors),
                Collections.emptyList(),
                Collections.emptyList(),
                expectedSeniority
        );
    }

    private List<String> extractLines(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        String sanitized = text.replace("•", "\n").replace("·", "\n");
        return Arrays.stream(sanitized.split("\\r?\\n"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void extractMajorEntries(List<String> source, List<String> majors) {
        if (source == null || source.isEmpty()) {
            return;
        }
        Iterator<String> iterator = source.iterator();
        while (iterator.hasNext()) {
            String entry = iterator.next();
            if (looksLikeMajor(entry)) {
                majors.add(normalizeMajor(entry));
                iterator.remove();
            }
        }
    }

    private boolean looksLikeMajor(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("학과")
                || normalized.contains("전공")
                || normalized.contains("학부")
                || normalized.contains("major")
                || normalized.contains("department of");
    }

    private String normalizeMajor(String raw) {
        if (!StringUtils.hasText(raw)) {
            return raw;
        }
        String sanitized = raw
                .replaceAll("^[\\s\\-•·\\d\\.\\)\\(]+", "")
                .replaceAll("[\\s\\-•·\\d\\.\\)\\(]+$", "")
                .trim();
        return sanitized.isEmpty() ? raw.trim() : sanitized;
    }

    private List<String> deduplicate(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                ordered.add(value.trim());
            }
        }
        return new ArrayList<>(ordered);
    }

    private TargetRoleEntity buildDynamicTarget(JobPostingEntity jobPosting, JobRequirementPayload requirements) {
        TargetRoleEntity target = new TargetRoleEntity();
        target.setRoleId("job-" + jobPosting.getId());
        target.setName(jobPosting.getTitle());
        target.setExpectedSeniority(
                StringUtils.hasText(requirements.expectedSeniority())
                        ? requirements.expectedSeniority()
                        : jobPosting.getCareerLevel()
        );
        target.setRequiredSkills(new ArrayList<>(toWeightedSkills(requirements.requiredSkills(), 2.0)));
        target.setBonusSkills(new ArrayList<>(toWeightedSkills(requirements.preferredSkills(), 0.6)));
        target.setMajorMapping(new ArrayList<>(toWeightedMajors(requirements)));

        List<String> recommendedCerts = !requirements.requiredCertifications().isEmpty()
                ? requirements.requiredCertifications()
                : requirements.preferredCertifications();
        target.setRecommendedCerts(new ArrayList<>(recommendedCerts));
        return target;
    }

    private List<WeightedSkill> toWeightedSkills(List<String> names, double weight) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        return names.stream()
                .filter(StringUtils::hasText)
                .map(name -> new WeightedSkill(name, weight))
                .toList();
    }

    private List<WeightedMajor> toWeightedMajors(JobRequirementPayload requirements) {
        List<WeightedMajor> majors = new ArrayList<>();
        if (requirements.requiredMajors() != null) {
            majors.addAll(requirements.requiredMajors().stream()
                    .filter(StringUtils::hasText)
                    .map(name -> new WeightedMajor(name, 1.0))
                    .toList());
        }
        if (requirements.preferredMajors() != null) {
            majors.addAll(requirements.preferredMajors().stream()
                    .filter(StringUtils::hasText)
                    .map(name -> new WeightedMajor(name, 0.6))
                    .toList());
        }
        return majors;
    }

    private JobFitScoreResponse toResponse(JobFitScoreEntity entity,
                                           JobPostingEntity jobPosting,
                                           List<RoleFitResponse.MissingSkill> missingSkills,
                                           List<String> recommendations,
                                           List<ImprovementItem> improvements,
                                           JobRequirementPayload requirements,
                                           boolean cached) {

        RoleFitResponse.Breakdown breakdown = new RoleFitResponse.Breakdown(
                defaultDouble(entity.getSkillFit()),
                defaultDouble(entity.getExperienceFit()),
                defaultDouble(entity.getEducationFit()),
                defaultDouble(entity.getEvidenceFit())
        );

        return new JobFitScoreResponse(
                jobPosting.getId(),
                entity.getUserId(),
                jobPosting.getTitle(),
                jobPosting.getCompanyName(),
                defaultDouble(entity.getTotalScore()),
                breakdown,
                missingSkills,
                recommendations,
                improvements,
                requirements,
                cached,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt()
        );
    }

    private double defaultDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value for job fit score: {}", e.getMessage());
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize job fit column: {}", e.getMessage());
            return fallback;
        }
    }

    private JobRequirementPayload readRequirements(String json) {
        if (!StringUtils.hasText(json)) {
            return JobRequirementPayload.empty();
        }
        try {
            return objectMapper.readValue(json, JobRequirementPayload.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize job requirements: {}", e.getMessage());
            return JobRequirementPayload.empty();
        }
    }
}

