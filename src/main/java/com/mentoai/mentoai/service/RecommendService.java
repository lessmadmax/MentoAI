package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityRecommendationResponse;
import com.mentoai.mentoai.controller.dto.ActivityResponse;
import com.mentoai.mentoai.controller.dto.CalendarEventUpsertRequest;
import com.mentoai.mentoai.controller.dto.RecommendRequest;
import com.mentoai.mentoai.controller.dto.RecommendResponse;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.CalendarEventType;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.UserRepository;
import com.mentoai.mentoai.service.CalendarEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
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
    private final CalendarEventService calendarEventService;

    @Value("${recommendation.vector-search.enabled:true}")
    private boolean vectorSearchEnabled;
    
    // 사용자 맞춤 활동 추천 (targetRole 기반)
    @Transactional(readOnly = true)
    public List<ActivityEntity> getRecommendations(Long userId, Integer limit, String type, Boolean campusOnly) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;

        if (!vectorSearchEnabled) {
            Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
            ActivityType activityType = parseActivityType(type);
            return activityRepository.findByFilters(
                    null,
                    activityType,
                    campusOnly,
                    null,
                    pageable
            ).getContent();
        }

        UserProfileResponse profile = userProfileService.getProfile(userId);
        String targetRoleId = profile.targetRoleId();
        if (targetRoleId == null || targetRoleId.isBlank()) {
            log.warn("User {} has no targetRoleId configured. Returning empty recommendations.", userId);
            return List.of();
        }

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
        String targetRoleId = userProfile.targetRoleId();
        Double currentRoleFitScore = calculateRoleFitScore(request.userId(), targetRoleId);

        // 2. 관련 활동 검색 (Retrieval)
        List<ActivityEntity> candidateActivities = retrieveRelevantActivities(
                request, userProfile, request.getTopKOrDefault() * 2
        );

        if (candidateActivities.isEmpty()) {
            log.warn("No personalized activities found for user {}. Falling back to recent activities with no filters.", request.userId());
            candidateActivities = fetchRecentActivities(request.getTopKOrDefault() * 3);
            if (candidateActivities.isEmpty()) {
                return new RecommendResponse(List.of());
            }
            debugCandidateTitles("recent-fallback", candidateActivities);
        }
        log.info("[recommend] candidates retrieved size={} topK={}", candidateActivities.size(), request.getTopKOrDefault());
        debugCandidateTitles("retrieved", candidateActivities);
        
        // 3. Gemini에 RAG 프롬프트 구성 및 전송
        String prompt = buildRAGPrompt(request, userProfile, candidateActivities);
        log.debug("[/recommend] RAG prompt (userId={}): {}", request.userId(), prompt);
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
            log.debug("[recommend] raw Gemini response: {}", geminiResponse);
            // 4. Gemini 응답 파싱하여 구조화된 결과 반환
            List<RecommendResponse.RecommendItem> items = parseGeminiRecommendationResponse(
                    geminiResponse,
                    candidateActivities,
                    request.getTopKOrDefault(),
                    request.userId(),
                    targetRoleId,
                    currentRoleFitScore
            );
            finalResponse = new RecommendResponse(items);
        } catch (Exception e) {
            log.error("Failed to generate recommendation from Gemini API", e);
            finalResponse = fallbackToScoreBasedRecommendation(request, candidateActivities, currentRoleFitScore);
            geminiResponse = "FALLBACK_USED: " + e.getMessage();
        }
        log.info("[recommend] final items size={} (after LLM/fallback)", finalResponse.items().size());

        recommendChatLogService.completeLog(
                chatLog.getId(),
                geminiResponse,
                finalResponse,
                "gemini-2.5-flash"
        );

        autoAddCalendarEvents(request.userId(), finalResponse.items(), chatLog.getId());
        return finalResponse;
    }
    
    /**
     * 관련 활동 검색 (Retrieval)
     */
    private List<ActivityEntity> retrieveRelevantActivities(
            RecommendRequest request,
            UserProfileResponse userProfile,
            int limit) {

        RecommendIntent intent = inferIntent(request);
        log.info("[recommend] intent={} query='{}' vectorEnabled={}", intent.normalizedIntent(), request.query(), vectorSearchEnabled);

        if (!vectorSearchEnabled) {
            log.debug("Vector search disabled; using intent-aware fallback.");
            return fallbackActivities(request, limit, intent);
        }

        String targetRoleId = userProfile.targetRoleId();
        int fetchSize = Math.min(limit * 3, 200);

        // 1) 하이브리드 검색: targetRole + profile + user query 결합
        List<ActivityRoleMatchService.RoleMatch> matches = activityRoleMatchService.hybridSearch(
                targetRoleId,
                userProfile.userId(),
                request.query(),
                fetchSize
        ).stream()
                .map(result -> new ActivityRoleMatchService.RoleMatch(
                        activityRoleMatchService.extractActivityId(result),
                        result.score(),
                        result.payload()
                ))
                .filter(m -> m.activityId() != null)
                .toList();

        // 2) targetRole만 (백업)
        if (matches.isEmpty() && StringUtils.hasText(targetRoleId)) {
            matches = activityRoleMatchService.findRoleMatches(targetRoleId, fetchSize);
        }

        // 3) 프로필만 (백업)
        if (matches.isEmpty()) {
            matches = activityRoleMatchService.findMatchesForUserProfile(userProfile.userId(), fetchSize);
        }

        if (matches.isEmpty()) {
            log.warn("No Qdrant matches found (hybrid/role/profile). Falling back to basic listing.");
            return fallbackActivities(request, limit, intent);
        }

        List<Long> ids = matches.stream()
                .map(ActivityRoleMatchService.RoleMatch::activityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, ActivityEntity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ActivityEntity::getId, Function.identity()));

        List<ActivityEntity> result = matches.stream()
                .map(match -> activityMap.get(match.activityId()))
                .filter(Objects::nonNull)
                .filter(activity -> matchesIntentFilters(activity, intent))
                .filter(activity -> matchesRequestFilters(activity, request, false))
                .limit(limit)
                .collect(Collectors.toList());
        log.info("[recommend] vector candidates={} after filters intent={} query='{}'", result.size(), intent.normalizedIntent(), request.query());
        debugCandidateTitles("vector", result);
        return result;
    }

    private List<ActivityEntity> fallbackActivities(RecommendRequest request, int limit, RecommendIntent intent) {
        int safeLimit = Math.max(limit, 1);
        Pageable pageable = PageRequest.of(0, safeLimit * 3, Sort.by(Sort.Direction.DESC, "createdAt"));

        List<ActivityEntity> candidates = activityRepository.findByFilters(
                        request.query(),
                        intent.inferredType(),
                        null,
                        null,
                        intent.requiredTags(),
                        pageable
                ).getContent();

        List<ActivityEntity> filtered = candidates.stream()
                .filter(activity -> matchesIntentFilters(activity, intent))
                .filter(activity -> matchesRequestFilters(activity, request, false))
                .limit(safeLimit)
                .collect(Collectors.toList());
        log.info("[recommend] fallback candidates={} filtered={}", candidates.size(), filtered.size());
        debugCandidateTitles("fallback", filtered);

        if (filtered.isEmpty()) {
            log.info("No activities matched intent {} and query '{}'. Falling back to recent activities.",
                    intent.normalizedIntent(), request.query());
            candidates = activityRepository.findByFilters(
                            null,
                            intent.inferredType(),
                            null,
                            null,
                            intent.requiredTags(),
                            pageable
                    ).getContent();

            filtered = candidates.stream()
                    .filter(activity -> matchesIntentFilters(activity, intent))
                    .filter(activity -> matchesRequestFilters(activity, request, true))
                    .limit(safeLimit)
                    .collect(Collectors.toList());
            log.info("[recommend] fallback(second) candidates={} filtered={}", candidates.size(), filtered.size());
            debugCandidateTitles("fallback-2", filtered);
        }

        return filtered;
    }

    private List<ActivityEntity> fetchRecentActivities(int limit) {
        int safe = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(0, safe, Sort.by(Sort.Direction.DESC, "createdAt"));
        return activityRepository.findByFilters(
                null,
                null,
                null,
                null,
                pageable
        ).getContent();
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
        if (StringUtils.hasText(request.query())) {
            prompt.append(request.query())
                    .append("\n")
                    .append("위 질의에는 ‘공모전’, ‘대회’, ‘콘테스트’, ‘행사’, ‘공고’ 등 다양한 표현이 섞여 있을 수 있습니다. 같은 의미의 변형 표현도 모두 동일하게 해석하고, 사용자의 의도에 맞춰 가장 관련 있는 활동을 찾으세요.\n");
        } else {
            prompt.append("사용자가 적합한 활동을 추천해 달라고 요청했습니다. 공모전/대회/공고/스터디 등 다양한 유형을 폭넓게 검토하여 사용자에게 도움이 될 만한 후보를 제안하세요.\n");
        }
        
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
        prompt.append("반드시 하나의 JSON 객체만 출력하세요. 추가 문장/설명/코드블록/백틱/언어 태그(json 등)를 절대 포함하지 마세요.\n");
        prompt.append("출력 형식 예시: {\"items\":[{\"activityIndex\":1,\"score\":95,\"reason\":\"...\"}]}\n");
        prompt.append("activityIndex는 위 후보 활동 목록 번호(1부터 시작)입니다. 사용자가 \"공모전 추천\", \"대회 추천\", \"행사 추천\", \"공고 추천\"처럼 다양한 표현을 사용하더라도 의미를 유연하게 해석하여 가장 관련도 높은 활동을 제안하세요.\n");
        prompt.append("score는 0-100 사이 숫자이고, reason에는 왜 해당 활동이 사용자에게 도움이 되는지 구체적으로 서술하세요. JSON 외 텍스트를 절대 포함하지 마세요.");
        
        return prompt.toString();
    }
    
    /**
     * Gemini 응답 파싱
     */
    private List<RecommendResponse.RecommendItem> parseGeminiRecommendationResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK,
            Long userId,
            String targetRoleId,
            Double currentRoleFitScore) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        try {
            // JSON 파싱 시도
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String normalizedResponse = extractJsonFragment(geminiResponse);
            com.fasterxml.jackson.databind.JsonNode jsonNode = objectMapper.readTree(normalizedResponse);
            
            com.fasterxml.jackson.databind.JsonNode itemsNode = jsonNode.path("items");
            if (itemsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode itemNode : itemsNode) {
                    int activityIndex = itemNode.path("activityIndex").asInt() - 1; // 1-based to 0-based
                    double score = itemNode.path("score").asDouble();
                    String reason = itemNode.path("reason").asText();
                    
                    if (activityIndex >= 0 && activityIndex < candidateActivities.size()) {
                        ActivityEntity activity = candidateActivities.get(activityIndex);
                        items.add(buildRecommendItem(
                                activity,
                                score,
                                reason,
                                userId,
                                targetRoleId,
                                currentRoleFitScore
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON response, trying text parsing", e);
            // JSON 파싱 실패 시 텍스트 기반 파싱 시도
            items = parseGeminiTextResponse(
                    geminiResponse,
                    candidateActivities,
                    topK,
                    userId,
                    targetRoleId,
                    currentRoleFitScore
            );
        }
        
        // 최대 topK개로 제한
        return items.stream()
                .limit(topK)
                .collect(Collectors.toList());
    }

    private String extractJsonFragment(String geminiResponse) {
        if (geminiResponse == null) {
            return "";
        }
        String trimmed = geminiResponse.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }
        int firstBrace = trimmed.indexOf('{');
        if (firstBrace >= 0) {
            int lastBrace = trimmed.lastIndexOf('}');
            if (lastBrace >= firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }
    
    /**
     * Gemini 텍스트 응답 파싱 (Fallback)
     */
    private List<RecommendResponse.RecommendItem> parseGeminiTextResponse(
            String geminiResponse,
            List<ActivityEntity> candidateActivities,
            int topK,
            Long userId,
            String targetRoleId,
            Double currentRoleFitScore) {
        
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        
        // 간단한 텍스트 파싱 (활동 제목 매칭)
        for (ActivityEntity activity : candidateActivities.stream().limit(topK).toList()) {
            if (geminiResponse.contains(activity.getTitle())) {
                items.add(buildRecommendItem(
                        activity,
                        75.0,
                        "사용자의 프로필과 질의를 기반으로 추천되었습니다.",
                        userId,
                        targetRoleId,
                        currentRoleFitScore
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
            List<ActivityEntity> candidateActivities,
            Double currentRoleFitScore) {
        
        // [MODIFIED] Guest fallback
        if (request.userId() == null) {
             return new RecommendResponse(candidateActivities.stream()
                .limit(request.getTopKOrDefault())
                .map(activity -> {
                    ActivityResponse response = ActivityMapper.toResponse(activity);
                    return new RecommendResponse.RecommendItem(
                            response,
                            0.0,
                            "Guest recommendation (fallback)",
                            0.0,
                            null,
                            null
                    );
                })
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
                .map(rec -> {
                    Double expectedAfter = (rec.roleFitScore() != null && rec.expectedScoreIncrease() != null)
                            ? clampScore(rec.roleFitScore() + rec.expectedScoreIncrease())
                            : null;
                    return new RecommendResponse.RecommendItem(
                            rec.activity(),
                            rec.recommendationScore(),
                            generateSimpleReason(rec),
                            rec.recommendationScore(),
                            rec.expectedScoreIncrease(),
                            expectedAfter
                    );
                })
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

    private RecommendResponse.RecommendItem buildRecommendItem(
            ActivityEntity activity,
            Double llmScore,
            String reason,
            Long userId,
            String targetRoleId,
            Double currentRoleFitScore) {
        Double expectedIncrease = (userId != null)
                ? calculateExpectedScoreIncrease(activity, userId, targetRoleId)
                : null;
        Double expectedAfter = (currentRoleFitScore != null && expectedIncrease != null)
                ? clampScore(currentRoleFitScore + expectedIncrease)
                : null;
        ActivityResponse activityResponse = ActivityMapper.toResponse(activity);
        Double systemScore = calculateSystemRecommendationScore(llmScore, currentRoleFitScore, expectedIncrease);
        return new RecommendResponse.RecommendItem(
                activityResponse,
                llmScore,
                reason,
                systemScore,
                expectedIncrease,
                expectedAfter
        );
    }

    private Double calculateSystemRecommendationScore(Double llmScore,
                                                      Double roleFitScore,
                                                      Double expectedIncrease) {
        double base = llmScore != null ? llmScore * 0.6 : 60.0;
        double roleContribution = roleFitScore != null ? roleFitScore * 0.3 : 0.0;
        double growthContribution = expectedIncrease != null ? expectedIncrease * 10.0 : 0.0;
        double total = clampScore(base + roleContribution + growthContribution);
        return Math.round(total * 10.0) / 10.0;
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private void autoAddCalendarEvents(Long userId,
                                       List<RecommendResponse.RecommendItem> items,
                                       Long recommendLogId) {
        if (userId == null || items == null || items.isEmpty()) {
            return;
        }
        for (RecommendResponse.RecommendItem item : items) {
            if (item.activity() == null || item.activity().activityId() == null) {
                continue;
            }
            try {
                ActivityEntity activity = activityRepository.findById(item.activity().activityId())
                        .orElse(null);
                if (activity == null) {
                    continue;
                }
                CalendarEventUpsertRequest request = buildCalendarEventRequest(activity, recommendLogId);
                if (request == null) {
                    continue;
                }
                calendarEventService.createCalendarEvent(userId, request);
            } catch (Exception e) {
                log.debug("Skip auto calendar add for user {} activity {}: {}", userId, item.activity().activityId(), e.getMessage());
            }
        }
    }

    private void debugCandidateTitles(String stage, List<ActivityEntity> activities) {
        if (!log.isInfoEnabled()) {
            return;
        }
        List<String> titles = activities.stream()
                .limit(10)
                .map(a -> a.getId() + ":" + a.getTitle())
                .toList();
        log.info("[recommend][{}] sample titles (up to 10): {}", stage, titles);
    }

    private CalendarEventUpsertRequest buildCalendarEventRequest(ActivityEntity activity, Long recommendLogId) {
        LocalDateTime startAt = resolveStartAt(activity);
        if (startAt == null) {
            return null;
        }
        LocalDateTime endAt = resolveEndAt(activity, startAt);
        return new CalendarEventUpsertRequest(
                CalendarEventType.ACTIVITY,
                activity.getTitle(),
                activity.getId(),
                null,
                recommendLogId,
                startAt,
                endAt,
                30
        );
    }

    private LocalDateTime resolveStartAt(ActivityEntity activity) {
        if (activity.getDates() != null && !activity.getDates().isEmpty()) {
            Optional<LocalDateTime> eventStart = activity.getDates().stream()
                    .filter(date -> date.getDateType() == ActivityDateEntity.DateType.EVENT_START)
                    .map(ActivityDateEntity::getDateValue)
                    .min(LocalDateTime::compareTo);
            if (eventStart.isPresent()) {
                return eventStart.get();
            }
            Optional<LocalDateTime> applyStart = activity.getDates().stream()
                    .filter(date -> date.getDateType() == ActivityDateEntity.DateType.APPLY_START)
                    .map(ActivityDateEntity::getDateValue)
                    .min(LocalDateTime::compareTo);
            if (applyStart.isPresent()) {
                return applyStart.get();
            }
        }
        if (activity.getPublishedAt() != null) {
            return activity.getPublishedAt();
        }
        if (activity.getCreatedAt() != null) {
            return activity.getCreatedAt();
        }
        return LocalDateTime.now().plusDays(1);
    }

    private LocalDateTime resolveEndAt(ActivityEntity activity, LocalDateTime startAt) {
        if (activity.getDates() != null && !activity.getDates().isEmpty()) {
            Optional<LocalDateTime> eventEnd = activity.getDates().stream()
                    .filter(date -> date.getDateType() == ActivityDateEntity.DateType.EVENT_END)
                    .map(ActivityDateEntity::getDateValue)
                    .max(LocalDateTime::compareTo);
            if (eventEnd.isPresent()) {
                return eventEnd.get();
            }
            Optional<LocalDateTime> applyEnd = activity.getDates().stream()
                    .filter(date -> date.getDateType() == ActivityDateEntity.DateType.APPLY_END)
                    .map(ActivityDateEntity::getDateValue)
                    .max(LocalDateTime::compareTo);
            if (applyEnd.isPresent()) {
                return applyEnd.get();
            }
        }
        return startAt.plusHours(2);
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

    private boolean matchesIntentFilters(ActivityEntity activity, RecommendIntent intent) {
        if (intent.inferredType() != null && activity.getType() != intent.inferredType()) {
            return false;
        }
        if (!intent.requiredTags().isEmpty()) {
            Set<String> activityTags = activity.getActivityTags() == null
                    ? Collections.emptySet()
                    : activity.getActivityTags().stream()
                    .map(tag -> tag.getTag() != null ? tag.getTag().getName() : null)
                    .filter(Objects::nonNull)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            for (String required : intent.requiredTags()) {
                if (!activityTags.contains(required.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }
        return true;
    }

    private RecommendIntent inferIntent(RecommendRequest request) {
        if (request.intentHint() != null) {
            return RecommendIntent.fromHint(request.intentHint());
        }
        if (!StringUtils.hasText(request.query())) {
            return RecommendIntent.defaultIntent();
        }
        String normalized = request.query().toLowerCase(Locale.ROOT);
        if (normalized.contains("공모전") || normalized.contains("대회") || normalized.contains("콘테스트")) {
            return RecommendIntent.contestIntent();
        }
        if (normalized.contains("채용") || normalized.contains("공고") || normalized.contains("취업")) {
            return RecommendIntent.jobIntent();
        }
        if (normalized.contains("스터디") || normalized.contains("공부") || normalized.contains("모임")) {
            return RecommendIntent.studyIntent();
        }
        return RecommendIntent.defaultIntent();
    }

    private record RecommendIntent(String normalizedIntent,
                                   ActivityType inferredType,
                                   List<String> requiredTags) {
        private static RecommendIntent fromHint(RecommendRequest.IntentHint hint) {
            ActivityType type = parseActivityTypeSafe(
                    hint.filter() != null ? hint.filter().activityType() : null);
            List<String> tags = hint.filter() != null && hint.filter().requiredTags() != null
                    ? hint.filter().requiredTags()
                    : List.of();
            return new RecommendIntent(
                    hint.normalizedIntent() != null ? hint.normalizedIntent() : "custom",
                    type,
                    tags
            );
        }

        private static RecommendIntent contestIntent() {
            return new RecommendIntent("contest", ActivityType.CONTEST, List.of("공모전", "대회"));
        }

        private static RecommendIntent jobIntent() {
            return new RecommendIntent("job", ActivityType.JOB, List.of("채용", "공고"));
        }

        private static RecommendIntent studyIntent() {
            return new RecommendIntent("study", ActivityType.STUDY, List.of("스터디"));
        }

        private static RecommendIntent defaultIntent() {
            return new RecommendIntent("general", null, List.of());
        }
    }

    private static ActivityType parseActivityTypeSafe(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ActivityType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
