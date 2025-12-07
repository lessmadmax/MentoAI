package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.JobRecommendRequest;
import com.mentoai.mentoai.controller.dto.JobRecommendResponse;
import com.mentoai.mentoai.controller.dto.JobPostingResponse;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.mapper.JobPostingMapper;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.integration.qdrant.QdrantSearchResult;
import com.mentoai.mentoai.repository.JobPostingRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobRecommendationService {

    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserProfileService userProfileService;
    private final GeminiService geminiService;
    private final JobPostingVectorService jobPostingVectorService;

    @Transactional(readOnly = true)
    public JobRecommendResponse recommend(JobRecommendRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (!userRepository.existsById(request.userId())) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.userId());
        }

        UserProfileResponse profile = userProfileService.getProfile(request.userId());
        String searchPrompt = buildSearchPrompt(profile, request.query());

        List<JobCandidate> candidates = retrieveCandidates(searchPrompt, request.fetchSize());
        if (candidates.isEmpty()) {
            return new JobRecommendResponse(List.of());
        }

        List<JobRecommendResponse.JobItem> items = candidates.stream()
                .map(candidate -> buildJobItem(candidate, profile))
                .toList();

        return new JobRecommendResponse(items);
    }

    private List<JobCandidate> retrieveCandidates(String prompt, int limit) {
        List<JobCandidate> candidates = vectorCandidates(prompt, limit);
        if (!candidates.isEmpty()) {
            return candidates;
        }

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Order.asc("deadline"), Sort.Order.desc("createdAt"))
        );
        return jobPostingRepository.findAll(pageable).getContent().stream()
                .map(job -> new JobCandidate(job, null))
                .toList();
    }

    private List<JobCandidate> vectorCandidates(String prompt, int limit) {
        if (!jobPostingVectorService.isVectorSearchEnabled() || !StringUtils.hasText(prompt)) {
            return List.of();
        }

        try {
            List<Double> embedding = geminiService.generateEmbedding(prompt);
            List<QdrantSearchResult> results = jobPostingVectorService.search(embedding, limit);
            if (results.isEmpty()) {
                return List.of();
            }

            List<Long> ids = results.stream()
                    .map(jobPostingVectorService::extractJobPostingId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) {
                return List.of();
            }

            Map<Long, JobPostingEntity> jobMap = jobPostingRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(JobPostingEntity::getId, job -> job));

            List<JobCandidate> ordered = new ArrayList<>();
            for (QdrantSearchResult result : results) {
                Long jobId = jobPostingVectorService.extractJobPostingId(result);
                if (jobId == null) {
                    continue;
                }
                JobPostingEntity entity = jobMap.get(jobId);
                if (entity == null) {
                    continue;
                }
                ordered.add(new JobCandidate(entity, clampSimilarity(result.score())));
            }
            return ordered;
        } catch (Exception e) {
            log.warn("Job posting vector search failed. Falling back to DB search: {}", e.getMessage());
            return List.of();
        }
    }

    private JobRecommendResponse.JobItem buildJobItem(JobCandidate candidate, UserProfileResponse profile) {
        JobPostingEntity jobPosting = candidate.jobPosting();
        double skillOverlap = calculateSkillOverlap(jobPosting, profile);
        double interestAlignment = calculateInterestAlignment(jobPosting, profile);

        double similarityScore = candidate.similarity() != null ? candidate.similarity() * 100.0 : 0.0;
        double finalScore = combineScore(candidate.similarity(), skillOverlap, interestAlignment);

        String reason = buildReason(jobPosting, profile, skillOverlap, candidate.similarity(), interestAlignment);
        JobPostingResponse response = JobPostingMapper.toResponse(jobPosting);

        Double similarity = candidate.similarity() != null ? round(similarityScore) : null;
        Double overlapValue = skillOverlap > 0 ? round(skillOverlap * 100.0) : null;

        return new JobRecommendResponse.JobItem(
                response,
                round(finalScore),
                reason,
                similarity,
                overlapValue
        );
    }

    private double combineScore(Double similarity, double skillOverlap, double interestAlignment) {
        double base = similarity != null ? similarity * 70 : 40;
        double skillBoost = skillOverlap * 20;
        double interestBoost = interestAlignment * 10;
        return Math.min(100.0, base + skillBoost + interestBoost);
    }

    private double calculateSkillOverlap(JobPostingEntity jobPosting, UserProfileResponse profile) {
        Set<String> jobSkills = new HashSet<>();
        if (jobPosting.getSkills() != null) {
            for (var skill : jobPosting.getSkills()) {
                if (StringUtils.hasText(skill.getSkillName())) {
                    jobSkills.add(skill.getSkillName().toLowerCase(Locale.ROOT).trim());
                }
            }
        }
        if (jobSkills.isEmpty() || CollectionUtils.isEmpty(profile.techStack())) {
            return 0.0;
        }

        long matches = profile.techStack().stream()
                .map(UserProfileResponse.Skill::name)
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase(Locale.ROOT).trim())
                .filter(jobSkills::contains)
                .count();
        return Math.min(1.0, matches / (double) jobSkills.size());
    }

    private double calculateInterestAlignment(JobPostingEntity jobPosting, UserProfileResponse profile) {
        if (CollectionUtils.isEmpty(profile.interestDomains())) {
            return 0.0;
        }

        String corpus = buildCorpus(jobPosting);
        if (!StringUtils.hasText(corpus)) {
            return 0.0;
        }
        String lowerCorpus = corpus.toLowerCase(Locale.ROOT);

        long matches = profile.interestDomains().stream()
                .filter(StringUtils::hasText)
                .map(domain -> domain.toLowerCase(Locale.ROOT))
                .filter(lowerCorpus::contains)
                .count();
        return Math.min(1.0, matches / (double) profile.interestDomains().size());
    }

    private String buildReason(JobPostingEntity jobPosting,
                               UserProfileResponse profile,
                               double skillOverlap,
                               Double similarity,
                               double interestAlignment) {
        StringBuilder reason = new StringBuilder();
        if (StringUtils.hasText(jobPosting.getCompanyName())) {
            reason.append(jobPosting.getCompanyName()).append(" ");
        }
        if (StringUtils.hasText(jobPosting.getTitle())) {
            reason.append(jobPosting.getTitle()).append(" 공고는 ");
        }
        if (StringUtils.hasText(profile.targetRoleId())) {
            reason.append("사용자의 타깃 직무(")
                    .append(profile.targetRoleId())
                    .append(")와 연관된 키워드를 포함하고 있습니다. ");
        }
        if (skillOverlap > 0) {
            reason.append("요구 기술 중 ")
                    .append(Math.round(skillOverlap * 100))
                    .append("%가 보유 기술과 일치합니다. ");
        }
        if (interestAlignment > 0) {
            reason.append("관심 도메인과도 겹치는 표현이 있습니다. ");
        }
        if (similarity != null) {
            reason.append("벡터 유사도 ").append(String.format("%.2f", similarity)).append("로 높은 관련성을 보였습니다. ");
        }
        if (jobPosting.getDeadline() != null) {
            reason.append("마감일은 ").append(jobPosting.getDeadline()).append(" 입니다.");
        }
        return reason.toString().trim();
    }

    private String buildSearchPrompt(UserProfileResponse profile, String freeQuery) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(freeQuery)) {
            builder.append("사용자 요청: ").append(freeQuery.trim()).append(". ");
        }
        if (profile.targetRoleId() != null) {
            builder.append("Target role: ").append(profile.targetRoleId()).append(". ");
        }
        if (profile.university() != null) {
            builder.append("Major: ").append(profile.university().major()).append(". ");
        }
        if (!CollectionUtils.isEmpty(profile.techStack())) {
            builder.append("Skills: ")
                    .append(profile.techStack().stream()
                            .map(UserProfileResponse.Skill::name)
                            .filter(StringUtils::hasText)
                            .collect(Collectors.joining(", ")))
                    .append(". ");
        }
        if (!CollectionUtils.isEmpty(profile.interestDomains())) {
            builder.append("Interest domains: ")
                    .append(String.join(", ", profile.interestDomains()))
                    .append(". ");
        }
        if (builder.length() == 0) {
            builder.append("Entry level university student looking for relevant job postings.");
        }
        return builder.toString();
    }

    private String buildCorpus(JobPostingEntity jobPosting) {
        StringBuilder builder = new StringBuilder();
        appendIfPresent(builder, jobPosting.getDescription());
        appendIfPresent(builder, jobPosting.getRequirements());
        appendIfPresent(builder, jobPosting.getBenefits());
        appendIfPresent(builder, jobPosting.getJobSector());
        appendIfPresent(builder, jobPosting.getCareerLevel());
        appendIfPresent(builder, jobPosting.getEducationLevel());
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(value).append(" ");
        }
    }

    private double clampSimilarity(double rawScore) {
        if (Double.isNaN(rawScore)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, rawScore));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record JobCandidate(JobPostingEntity jobPosting, Double similarity) {
    }
}

