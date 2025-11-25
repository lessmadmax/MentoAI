package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityRecommendationResponse;
import com.mentoai.mentoai.controller.dto.ActivityResponse;
import com.mentoai.mentoai.controller.dto.RecommendRequest;
import com.mentoai.mentoai.controller.dto.RecommendResponse;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {
    
    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final RoleFitService roleFitService;
    private final UserProfileService userProfileService;
    private final ActivityRoleMatchService activityRoleMatchService;
    private final RecommendChatLogService recommendChatLogService;

    @Value("${recommendation.vector-search.enabled:false}")
    private boolean vectorSearchEnabled;
    
    // 사용자 맞춤 활동 추천 (targetRole 기반)
    @Transactional(readOnly = true)
    public List<ActivityEntity> getRecommendations(Long userId, Integer limit, String type, Boolean campusOnly) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }

        UserProfileResponse profile = userProfileService.getProfile(userId);
        String targetRoleId = profile.targetRoleId();
        if (targetRoleId == null || targetRoleId.isBlank()) {
            log.warn("User {} has no targetRoleId configured. Returning empty recommendations.", userId);
            return List.of();
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
        int fetchSize = Math.min(Math.max(safeLimit * 2, safeLimit), 200);
        List<ActivityRoleMatchService.RoleMatch> matches =
                activityRoleMatchService.findRoleMatches(targetRoleId, fetchSize);
        if (matches.isEmpty()) {
            log.warn("No Qdrant matches for user {} and targetRole {}", userId, targetRoleId);
            return List.of();
        }

        List<Long> ids = matches.stream()
                .map(ActivityRoleMatchService.RoleMatch::activityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ActivityEntity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ActivityEntity::getId, Function.identity()));

        ActivityType activityType = parseActivityType(type);

        return matches.stream()
                .map(match -> activityMap.get(match.activityId()))
                .filter(Objects::nonNull)
                .filter(activity -> matchesBasicFilters(activity, activityType, campusOnly))
                .limit(safeLimit)
                .collect(Collectors.toList());
    }
    
    // 의미 기반 검색 (간단한 키워드 매칭)
    public List<ActivityEntity> semanticSearch(String query, Integer limit, String userId) {
        return semanticSearchWithScores(query, limit, userId).stream()
                .map(SemanticSearchResult::activity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SemanticSearchResult> semanticSearchWithScores(String query, Integer limit, String userId) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 필수입니다.");
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
        
        // Gemini 임베딩 기반 검색 시도
        try {
            List<SemanticSearchResult> embeddingResults = semanticSearchWithEmbedding(query, safeLimit, userId);
            if (!embeddingResults.isEmpty()) {
                return embeddingResults;
            }
        } catch (Exception e) {
            log.warn("Gemini embedding search failed, falling back to keyword search", e);
        }
        
        // Fallback: 키워드 기반 검색
        List<String> searchTerms = expandSearchTerms(query);
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (String term : searchTerms) {
            Pageable pageable = PageRequest.of(0, safeLimit * 2, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            List<ActivityEntity> results = activityRepository.findByFilters(
                    term.toLowerCase(),
                    null, // 타입 필터 없음
                    null, // 캠퍼스 필터 없음
                    null, // 상태 필터 없음
                    pageable
            ).getContent();
            
            // 각 활동에 대해 점수 계산
            for (ActivityEntity activity : results) {
                double score = calculateSearchScore(activity, term, searchTerms);
                activityScores.merge(activity, score, Double::sum);
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(safeLimit)
                .map(entry -> new SemanticSearchResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    // Gemini 임베딩 기반 의미 검색
    private List<SemanticSearchResult> semanticSearchWithEmbedding(String query, int limit, String userId) {
        // 검색어 임베딩 생성
        List<Double> queryEmbedding = geminiService.generateEmbedding(query);
        
        // 활동 목록 조회 (최근 활동 위주)
        Pageable pageable = PageRequest.of(0, limit * 3, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ActivityEntity> activities = activityRepository.findByFilters(
                null, null, null, null, pageable
        ).getContent();
        
        // 각 활동의 텍스트를 임베딩으로 변환하고 유사도 계산
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (ActivityEntity activity : activities) {
            try {
                String activityText = buildActivityText(activity);
                List<Double> activityEmbedding = geminiService.generateEmbedding(activityText);
                
                double similarity = geminiService.cosineSimilarity(queryEmbedding, activityEmbedding);
                
                if (similarity > 0.3) { // 최소 유사도 임계값
                    activityScores.put(activity, similarity * 100); // 0-100 점수로 변환
                }
            } catch (Exception e) {
                log.warn("Failed to generate embedding for activity {}: {}", activity.getId(), e.getMessage());
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new SemanticSearchResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
    
    // 활동의 제목, 내용, 태그를 결합하여 텍스트 생성
    private String buildActivityText(ActivityEntity activity) {
        StringBuilder text = new StringBuilder();
        
        if (activity.getTitle() != null) {
            text.append(activity.getTitle()).append(" ");
        }
        
        if (activity.getSummary() != null) {
            text.append(activity.getSummary()).append(" ");
        }
        
        if (activity.getContent() != null) {
            // 내용이 너무 길면 앞부분만 사용
            String content = activity.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            text.append(content).append(" ");
        }
        
        // 태그 추가
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                if (activityTag.getTag() != null && activityTag.getTag().getName() != null) {
                    text.append(activityTag.getTag().getName()).append(" ");
                }
            }
        }
        
        return text.toString().trim();
    }
    
    // 검색어 확장 (동의어, 관련어 추가)
    private List<String> expandSearchTerms(String query) {
        List<String> terms = new ArrayList<>();
        terms.add(query); // 원본 검색어
        
        // 간단한 동의어 매핑
        Map<String, List<String>> synonyms = Map.of(
            "개발", List.of("프로그래밍", "코딩", "소프트웨어"),
            "디자인", List.of("UI", "UX", "그래픽"),
            "마케팅", List.of("홍보", "광고", "브랜딩"),
            "스터디", List.of("공부", "학습", "연구"),
            "취업", List.of("채용", "구직", "인턴"),
            "창업", List.of("스타트업", "사업", "비즈니스")
        );
        
        String lowerQuery = query.toLowerCase();
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                terms.addAll(entry.getValue());
            }
            for (String synonym : entry.getValue()) {
                if (lowerQuery.contains(synonym)) {
                    terms.add(entry.getKey());
                    terms.addAll(entry.getValue());
                }
            }
        }
        
        return terms.stream().distinct().collect(Collectors.toList());
    }
    
    // 검색 점수 계산
    private double calculateSearchScore(ActivityEntity activity, String term, List<String> allTerms) {
        double score = 0.0;
        String title = activity.getTitle().toLowerCase();
        String content = activity.getContent() != null ? activity.getContent().toLowerCase() : "";
        
        // 제목에서 매칭 (가중치 높음)
        if (title.contains(term.toLowerCase())) {
            score += 2.0;
        }
        
        // 내용에서 매칭
        if (content.contains(term.toLowerCase())) {
            score += 1.0;
        }
        
        // 정확한 매칭 보너스
        if (title.equals(term.toLowerCase())) {
            score += 3.0;
        }
        
        // 태그 매칭 (활동 태그가 있다면)
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                String tagName = activityTag.getTag().getName().toLowerCase();
                if (tagName.contains(term.toLowerCase())) {
                    score += 1.5;
                }
            }
        }
        
        return score;
    }
    
    // 인기 활동 조회
    @Transactional(readOnly = true)
    public List<ActivityEntity> getTrendingActivities(Integer limit, String type) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        ActivityType activityType = null;
        if (type != null && !type.isEmpty()) {
            try {
                activityType = ActivityType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 잘못된 타입이면 무시
            }
        }
        
        return activityRepository.findByFilters(
                null, // 검색어 없음
                activityType,
                null, // 캠퍼스 필터 없음
                null, // 상태 필터 없음
                pageable
        ).getContent();
    }
    
    // 유사 활동 추천
    @Transactional(readOnly = true)
    public List<ActivityEntity> getSimilarActivities(Long activityId, Integer limit) {
        Optional<ActivityEntity> targetActivity = activityRepository.findById(activityId);
        if (targetActivity.isEmpty()) {
            throw new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId);
        }
        
        ActivityEntity activity = targetActivity.get();
        
        // 같은 유형의 활동들 중에서 추천
        Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        List<ActivityEntity> similarActivities = activityRepository.findByFilters(
                null, // 검색어 없음
                activity.getType(),
                activity.getIsCampus(),
                null, // 상태 필터 없음
                pageable
        ).getContent();
        
        // 자기 자신 제외
        return similarActivities.stream()
                .filter(a -> !a.getId().equals(activityId))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public record SemanticSearchResult(ActivityEntity activity, double score) {
    }
    
    // 점수 포함 활동 추천 (targetRole 기반)
    @Transactional(readOnly = true)
    public List<ActivityRecommendationResponse> getRecommendationsWithScores(
            Long userId, Integer limit, String type, Boolean campusOnly, String targetRoleOverride) {

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }

        UserProfileResponse profile = userProfileService.getProfile(userId);
        String targetRoleId = (targetRoleOverride != null && !targetRoleOverride.isBlank())
                ? targetRoleOverride.trim()
                : profile.targetRoleId();

        if (targetRoleId == null || targetRoleId.isBlank()) {
            log.warn("User {} requested scored recommendations but targetRoleId is missing.", userId);
            return List.of();
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
        int fetchSize = Math.min(safeLimit * 3, 200);
        ActivityType activityType = parseActivityType(type);

        if (!vectorSearchEnabled) {
            log.debug("Vector search disabled. Using basic listing for scored recommendations.");
            return buildRecommendationsFromBasicListing(userId, safeLimit, activityType, campusOnly, targetRoleId);
        }

        List<ActivityRoleMatchService.RoleMatch> matches =
                activityRoleMatchService.findRoleMatches(targetRoleId, fetchSize);
        if (matches.isEmpty()) {
            log.warn("No Qdrant matches for scored recommendations: user={}, targetRole={}", userId, targetRoleId);
            return buildRecommendationsFromBasicListing(userId, safeLimit, activityType, campusOnly, targetRoleId);
        }

        List<Long> ids = matches.stream()
                .map(ActivityRoleMatchService.RoleMatch::activityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ActivityEntity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ActivityEntity::getId, Function.identity()));

        Double roleFitScore = calculateRoleFitScore(userId, targetRoleId);

        List<ActivityRecommendationResponse> responses = new ArrayList<>();
        for (ActivityRoleMatchService.RoleMatch match : matches) {
            ActivityEntity activity = activityMap.get(match.activityId());
            if (activity == null) {
                continue;
            }
            if (!matchesBasicFilters(activity, activityType, campusOnly)) {
                continue;
            }

            double similarityScore = match.score() * 100.0;
            double recommendationScore = roleFitScore != null
                    ? (similarityScore * 0.7) + (roleFitScore * 0.3)
                    : similarityScore;

            Double expectedScoreIncrease = calculateExpectedScoreIncrease(activity, userId, targetRoleId);
            ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
            responses.add(new ActivityRecommendationResponse(
                    activityResponse,
                    Math.round(recommendationScore * 10.0) / 10.0,
                    roleFitScore,
                    expectedScoreIncrease
            ));

            if (responses.size() >= safeLimit) {
                break;
            }
        }

        return responses;
    }

    private List<ActivityRecommendationResponse> buildRecommendationsFromBasicListing(
            Long userId,
            int safeLimit,
            ActivityType activityType,
            Boolean campusOnly,
            String targetRoleId) {
        Pageable pageable = PageRequest.of(0, safeLimit * 3, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ActivityEntity> candidates = activityRepository.findByFilters(
                null,
                activityType,
                campusOnly,
                null, // allow OPEN + NULL statuses
                pageable
        ).getContent();

        Double roleFitScore = calculateRoleFitScore(userId, targetRoleId);
        List<ActivityRecommendationResponse> responses = new ArrayList<>();

        for (ActivityEntity activity : candidates) {
            if (!matchesBasicFilters(activity, activityType, campusOnly)) {
                continue;
            }

            double similarityScore = Math.max(20.0, 70.0 - responses.size() * 5.0);
            double recommendationScore = roleFitScore != null
                    ? (similarityScore * 0.7) + (roleFitScore * 0.3)
                    : similarityScore;
            Double expectedScoreIncrease = calculateExpectedScoreIncrease(activity, userId, targetRoleId);

            ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
            responses.add(new ActivityRecommendationResponse(
                    activityResponse,
                    Math.round(recommendationScore * 10.0) / 10.0,
                    roleFitScore,
                    expectedScoreIncrease
            ));

            if (responses.size() >= safeLimit) {
                break;
            }
        }

        return responses;
    }
    
    // 활동 완료 시 예상 점수 증가량 계산
    private Double calculateExpectedScoreIncrease(ActivityEntity activity, Long userId, String targetRole) {
        if (targetRole == null || targetRole.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 활동 유형에 따른 점수 증가량 추정
            double baseIncrease = 0.0;
            if (activity.getType() == ActivityType.CONTEST) {
                baseIncrease = 3.0; // 공모전 완료 시 평균 3점 증가
            } else if (activity.getType() == ActivityType.STUDY) {
                baseIncrease = 2.0; // 스터디 완료 시 평균 2점 증가
            } else if (activity.getType() == ActivityType.JOB) {
                baseIncrease = 1.5; // 취업 활동 완료 시 평균 1.5점 증가
            } else {
                baseIncrease = 1.0; // 기타 활동 완료 시 평균 1점 증가
            }
            
            // 활동의 태그와 타겟 직무의 스킬 매칭도에 따라 가중치 적용
            // 간단한 추정: 활동이 타겟 직무와 관련이 있으면 추가 증가
            if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
                // 태그가 있으면 약간의 보너스
                baseIncrease *= 1.1;
            }
            
            return Math.round(baseIncrease * 10.0) / 10.0; // 소수점 1자리
        } catch (Exception e) {
            log.debug("Failed to calculate expected score increase: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * RAG 기반 맞춤 추천 (사용자 프롬프트 기반)
     */
    @Transactional
    public RecommendResponse getRecommendationsByRequest(RecommendRequest request) {
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        
        if (!userRepository.existsById(request.userId())) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + request.userId());
        }
        
        // 1. 사용자 프로필 수집
        UserProfileResponse userProfile = userProfileService.getProfile(request.userId());

        // 2. 관련 활동 검색 (Retrieval)
        List<ActivityEntity> candidateActivities = retrieveRelevantActivities(
                request, userProfile, request.getTopKOrDefault() * 2
        );

        if (candidateActivities.isEmpty()) {
            log.warn("No personalized activities found for user {}", request.userId());
            return new RecommendResponse(List.of());
        }
        
        // 3. Gemini에 RAG 프롬프트 구성 및 전송
        String prompt = buildRAGPrompt(request, userProfile, candidateActivities);
        var chatLog = recommendChatLogService.createLog(
                request.userId(),
                userProfile.targetRoleId(),
                request,
                candidateActivities,
                prompt
        );

        String geminiResponse;
        RecommendResponse finalResponse;
        try {
            geminiResponse = geminiService.generateText(prompt);
            // 4. Gemini 응답 파싱하여 구조화된 결과 반환
            List<RecommendResponse.RecommendItem> items = parseGeminiRecommendationResponse(
                    geminiResponse, candidateActivities, request.getTopKOrDefault()
            );
            finalResponse = new RecommendResponse(items);
        } catch (Exception e) {
            log.error("Failed to generate recommendation from Gemini API", e);
            finalResponse = fallbackToScoreBasedRecommendation(request, candidateActivities);
            geminiResponse = "FALLBACK_USED: " + e.getMessage();
        }

        recommendChatLogService.completeLog(
                chatLog.getId(),
                geminiResponse,
                finalResponse,
                "gemini-2.5-flash"
        );

        return finalResponse;
    }
    
    /**
     * 관련 활동 검색 (Retrieval)
     */
    private List<ActivityEntity> retrieveRelevantActivities(
            RecommendRequest request,
            UserProfileResponse userProfile,
            int limit) {

        if (!vectorSearchEnabled) {
            log.debug("Vector DB integration disabled. Using fallback activity list only.");
            return fallbackActivities(request, limit);
        }

        String targetRoleId = userProfile.targetRoleId();
        if (targetRoleId == null || targetRoleId.isBlank()) {
            log.warn("User {} has no targetRoleId configured. Falling back to basic activity list.", userProfile.userId());
            return fallbackActivities(request, limit);
        }

        int fetchSize = Math.min(limit * 3, 200);
        List<ActivityRoleMatchService.RoleMatch> matches =
                activityRoleMatchService.findRoleMatches(targetRoleId, fetchSize);
        if (matches.isEmpty()) {
            log.warn("No Qdrant matches for target role {}. Falling back to basic listing.", targetRoleId);
            return fallbackActivities(request, limit);
        }

        List<Long> ids = matches.stream()
                .map(ActivityRoleMatchService.RoleMatch::activityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ActivityEntity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ActivityEntity::getId, Function.identity()));

        return matches.stream()
                .map(match -> activityMap.get(match.activityId()))
                .filter(Objects::nonNull)
                .filter(activity -> matchesRequestFilters(activity, request, false))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<ActivityEntity> fallbackActivities(RecommendRequest request, int limit) {
        int safeLimit = Math.max(limit, 1);
        Pageable pageable = PageRequest.of(0, safeLimit * 3, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 1. Try with query
        List<ActivityEntity> candidates = activityRepository.findByFilters(
                        request.query(),
                        null,
                        null,
                        null, // include entries with NULL status
                        pageable
                ).getContent();

        List<ActivityEntity> filtered = candidates.stream()
                .filter(activity -> matchesRequestFilters(activity, request, false))
                .limit(safeLimit)
                .collect(Collectors.toList());

        // 2. If empty results, try fetching ALL recent activities (ignoring query)
        if (filtered.isEmpty() && StringUtils.hasText(request.query())) {
            log.info("No activities matched query '{}'. Falling back to recent activities.", request.query());
            candidates = activityRepository.findByFilters(
                        null, // No query
                        null,
                        null,
                        null, // include entries with NULL status
                        pageable
                ).getContent();

            filtered = candidates.stream()
                    .filter(activity -> matchesRequestFilters(activity, request, true)) // Ignore query filter
                    .limit(safeLimit)
                    .collect(Collectors.toList());
        }

        return filtered;
    }
    
    /**
     * RAG 프롬프트 구성
     */
    private String buildRAGPrompt(
            RecommendRequest request,
            UserProfileResponse userProfile,
            List<ActivityEntity> activities) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 대학생 진로 상담 전문가입니다. 사용자의 프로필과 질의를 바탕으로 활동을 추천해주세요.\n\n");
        
        // 사용자 프로필 정보
        prompt.append("=== 사용자 프로필 ===\n");
        
        if (userProfile.university() != null) {
            prompt.append(String.format("대학: %s, 학년: %d학년, 전공: %s\n",
                    userProfile.university().universityName() != null ? userProfile.university().universityName() : "미입력",
                    userProfile.university().grade() != null ? userProfile.university().grade() : 0,
                    userProfile.university().major() != null ? userProfile.university().major() : "미입력"));
        }
        
        // 관심 분야
        if (userProfile.interestDomains() != null && !userProfile.interestDomains().isEmpty()) {
            prompt.append("관심 분야: ").append(String.join(", ", userProfile.interestDomains())).append("\n");
        }
        
        // 기술 스택
        if (userProfile.techStack() != null && !userProfile.techStack().isEmpty()) {
            List<String> skills = userProfile.techStack().stream()
                    .map(skill -> skill.name() + (skill.level() != null ? " (" + skill.level() + ")" : ""))
                    .toList();
            prompt.append("기술 스택: ").append(String.join(", ", skills)).append("\n");
        }
        
        // 경험
        if (userProfile.experiences() != null && !userProfile.experiences().isEmpty()) {
            prompt.append("주요 경험:\n");
            for (UserProfileResponse.Experience exp : userProfile.experiences()) {
                prompt.append(String.format("- %s: %s (%s)\n",
                        exp.type(), exp.title(), exp.organization()));
            }
        }
        
        // 자연어 질의
        prompt.append("\n=== 사용자 질의 ===\n");
        prompt.append(request.query() != null ? request.query() : "활동 추천을 요청합니다.").append("\n");
        
        // 선호 태그
        if (request.preferTags() != null && !request.preferTags().isEmpty()) {
            prompt.append("선호 태그: ").append(String.join(", ", request.preferTags())).append("\n");
        }
        
        // 후보 활동 목록
        prompt.append("\n=== 후보 활동 목록 ===\n");
        for (int i = 0; i < Math.min(activities.size(), 20); i++) {
            ActivityEntity activity = activities.get(i);
            prompt.append(String.format("[%d] %s\n", i + 1, activity.getTitle()));
            if (activity.getSummary() != null) {
                prompt.append("   요약: ").append(activity.getSummary()).append("\n");
            }
            if (activity.getActivityTags() != null && !activity.getActivityTags().isEmpty()) {
                List<String> tagNames = activity.getActivityTags().stream()
                        .map(at -> at.getTag().getName())
                        .toList();
                prompt.append("   태그: ").append(String.join(", ", tagNames)).append("\n");
            }
            prompt.append("\n");
        }
        
        // 추천 요청
        prompt.append("\n=== 요청사항 ===\n");
        prompt.append(String.format("위 정보를 바탕으로 사용자에게 가장 적합한 활동 %d개를 추천하고, ", request.getTopKOrDefault()));
        prompt.append("각 추천에 대해 구체적인 이유를 설명해주세요.\n");
        prompt.append("JSON 형식으로 반환해주세요:\n");
        prompt.append("{\n");
        prompt.append("  \"items\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"activityIndex\": 1,\n");
        prompt.append("      \"score\": 85.5,\n");
        prompt.append("      \"reason\": \"이 공모전은 백엔드 개발 경험을 쌓기에 적합합니다. 사용자의 Spring Boot 경험과 연계하여 실무 역량을 향상시킬 수 있습니다.\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("activityIndex는 위 후보 활동 목록의 번호(1부터 시작)입니다.\n");
        prompt.append("score는 0-100 사이의 추천 점수입니다.\n");
        prompt.append("reason은 사용자가 이해하기 쉬운 자연어로 작성해주세요.");
        
        return prompt.toString();
    }
    
    /**
     * Gemini 응답 파싱
     */
    private List<RecommendResponse.RecommendItem> parseGeminiRecommendationResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        try {
            // JSON 파싱 시도
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(geminiResponse);
            
            com.fasterxml.jackson.databind.JsonNode itemsNode = jsonNode.path("items");
            if (itemsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode itemNode : itemsNode) {
                    int activityIndex = itemNode.path("activityIndex").asInt() - 1; // 1-based to 0-based
                    double score = itemNode.path("score").asDouble();
                    String reason = itemNode.path("reason").asText();
                    
                    if (activityIndex >= 0 && activityIndex < candidateActivities.size()) {
                        ActivityEntity activity = candidateActivities.get(activityIndex);
                        ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
                        items.add(new RecommendResponse.RecommendItem(
                                activityResponse,
                                score,
                                reason
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response, trying text parsing", e);
            // JSON 파싱 실패 시 텍스트 기반 파싱 시도
            items = parseGeminiTextResponse(geminiResponse, candidateActivities, topK);
        }
        
        // 최대 topK개로 제한
        return items.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    /**
     * Gemini 텍스트 응답 파싱 (Fallback)
     */
    private List<RecommendResponse.RecommendItem> parseGeminiTextResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        // 간단한 텍스트 파싱 (활동 제목 매칭)
        for (ActivityEntity activity : candidateActivities.stream().limit(topK).toList()) {
            if (geminiResponse.contains(activity.getTitle())) {
                ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
                items.add(new RecommendResponse.RecommendItem(
                        activityResponse,
                        75.0, // 기본 점수
                        "사용자의 프로필과 질의를 기반으로 추천되었습니다."
                ));
            }
        }
        
        return items;
    }
    
    /**
     * Fallback: 점수 기반 추천
     */
    private RecommendResponse fallbackToScoreBasedRecommendation(
            RecommendRequest request,
            List<ActivityEntity> candidateActivities) {
        
        // [MODIFIED] Guest fallback
        if (request.userId() == null) {
             return new RecommendResponse(candidateActivities.stream()
                .limit(request.getTopKOrDefault())
                .map(activity -> new RecommendResponse.RecommendItem(
                        ActivityMapper.toResponse(activity),
                        0.0,
                        "Guest recommendation (fallback)"
                ))
                .toList());
        }

        UserProfileResponse profile = userProfileService.getProfile(request.userId());
        String targetRoleId = profile.targetRoleId();
        
        List<ActivityRecommendationResponse> scored = getRecommendationsWithScores(
                request.userId(),
                request.getTopKOrDefault(),
                null,
                null,
                targetRoleId
        );
        
        List<RecommendResponse.RecommendItem> items = scored.stream()
                .map(rec -> new RecommendResponse.RecommendItem(
                        rec.activity(),
                        rec.recommendationScore(),
                        generateSimpleReason(rec)
                ))
                .collect(Collectors.toList());
        
        return new RecommendResponse(items);
    }
    
    /**
     * 간단한 추천 이유 생성
     */
    private String generateSimpleReason(ActivityRecommendationResponse rec) {
        StringBuilder reason = new StringBuilder();
        if (rec.roleFitScore() != null) {
            reason.append(String.format("직무 적합도: %.1f점. ", rec.roleFitScore()));
        }
        reason.append("사용자의 목표 직무와 프로필을 기반으로 추천되었습니다.");
        return reason.toString();
    }

    private boolean matchesRequestFilters(ActivityEntity activity, RecommendRequest request, boolean ignoreQuery) {
        if (!ignoreQuery && request.query() != null && !request.query().isBlank()) {
            String normalizedQuery = request.query().trim().toLowerCase();
            if (!containsKeyword(activity, normalizedQuery)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesBasicFilters(ActivityEntity activity, ActivityType type, Boolean campusOnly) {
        if (type != null && activity.getType() != type) {
            return false;
        }
        if (campusOnly != null) {
            boolean isCampusActivity = Boolean.TRUE.equals(activity.getIsCampus());
            if (!campusOnly.equals(isCampusActivity)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsKeyword(ActivityEntity activity, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String title = activity.getTitle() != null ? activity.getTitle().toLowerCase() : "";
        if (title.contains(keyword)) {
            return true;
        }
        String summary = activity.getSummary() != null ? activity.getSummary().toLowerCase() : "";
        if (summary.contains(keyword)) {
            return true;
        }
        String content = activity.getContent() != null ? activity.getContent().toLowerCase() : "";
        return content.contains(keyword);
    }

    private Double calculateRoleFitScore(Long userId, String targetRoleId) {
        if (targetRoleId == null || targetRoleId.isBlank()) {
            return null;
        }
        try {
            var roleFitResponse = roleFitService.calculateRoleFit(userId, new RoleFitRequest(targetRoleId, null));
            return roleFitResponse.roleFitScore();
        } catch (Exception e) {
            log.warn("Failed to calculate role fit score for user {} and role {}: {}", userId, targetRoleId, e.getMessage());
            return null;
        }
    }

    private ActivityType parseActivityType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return ActivityType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unsupported activity type filter: {}", type);
            return null;
        }
    }
}
