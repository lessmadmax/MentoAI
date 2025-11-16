package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendService {
    
    private final ActivityRepository activityRepository;
    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    
    // 사용자 맞춤 활동 추천
    public List<ActivityEntity> getRecommendations(Long userId, Integer limit, String type, Boolean campusOnly) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        // 사용자 관심사 조회
        List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userId);
        
        if (userInterests.isEmpty()) {
            // 관심사가 없으면 일반적인 인기 활동 추천
            return getTrendingActivities(limit, type);
        }
        
        // 관심사 기반 추천 로직
        List<ActivityEntity> recommendations = new ArrayList<>();
        
        // 1. 관심사 태그와 매칭되는 활동들 찾기
        List<Long> tagIds = userInterests.stream()
                .map(UserInterestEntity::getTagId)
                .collect(Collectors.toList());
        
        Pageable pageable = PageRequest.of(0, limit * 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // 태그 매칭 활동 조회
        List<ActivityEntity> tagMatchedActivities = activityRepository.findByFilters(
                null, // 검색어 없음
                type != null ? ActivityType.valueOf(type.toUpperCase()) : null,
                campusOnly,
                null, // 상태 필터 없음
                pageable
        ).getContent();
        
        // 태그 매칭 점수 계산
        Map<ActivityEntity, Double> activityScores = new HashMap<>();
        
        for (ActivityEntity activity : tagMatchedActivities) {
            double score = calculateActivityScore(activity, userInterests);
            if (score > 0) {
                activityScores.put(activity, score);
            }
        }
        
        // 점수 순으로 정렬하여 추천
        recommendations = activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        // 추천이 부족하면 인기 활동으로 보완
        if (recommendations.size() < limit) {
            List<ActivityEntity> trending = getTrendingActivities(limit - recommendations.size(), type);
            recommendations.addAll(trending);
        }
        
        return recommendations;
    }
    
    // 활동 점수 계산 (관심사 기반)
    private double calculateActivityScore(ActivityEntity activity, List<UserInterestEntity> userInterests) {
        double score = 0.0;
        
        // 활동의 태그들과 사용자 관심사 매칭
        if (activity.getActivityTags() != null) {
            for (var activityTag : activity.getActivityTags()) {
                for (UserInterestEntity userInterest : userInterests) {
                    if (activityTag.getTag().getId().equals(userInterest.getTagId())) {
                        // 관심사 점수에 따라 가중치 적용
                        score += userInterest.getScore() * 0.3;
                    }
                }
            }
        }
        
        // 활동 유형 선호도 (간단한 규칙 기반)
        if (activity.getType() == ActivityType.STUDY) {
            score += 0.2;
        } else if (activity.getType() == ActivityType.CONTEST) {
            score += 0.1;
        }
        
        // 캠퍼스 활동 가중치
        if (activity.getIsCampus() != null && activity.getIsCampus()) {
            score += 0.1;
        }
        
        return score;
    }
    
    // 의미 기반 검색 (간단한 키워드 매칭)
    public List<ActivityEntity> semanticSearch(String query, Integer limit, String userId) {
        return semanticSearchWithScores(query, limit, userId).stream()
                .map(SemanticSearchResult::activity)
                .collect(Collectors.toList());
    }

    public List<SemanticSearchResult> semanticSearchWithScores(String query, Integer limit, String userId) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 필수입니다.");
        }

        int safeLimit = (limit == null || limit <= 0) ? 10 : limit;
        
        // 검색어 전처리 및 확장
        List<String> searchTerms = expandSearchTerms(query);
        
        // 각 검색어에 대해 활동 검색
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
        
        // 사용자 관심사 기반 가중치 적용
        if (userId != null && !userId.isEmpty()) {
            try {
                Long userIdLong = Long.valueOf(userId);
                List<UserInterestEntity> userInterests = userInterestRepository.findByUserIdOrderByScoreDesc(userIdLong);
                
                if (!userInterests.isEmpty()) {
                    activityScores.replaceAll((activity, score) -> {
                        double interestScore = calculateActivityScore(activity, userInterests);
                        return score + (interestScore * 0.3); // 관심사 가중치 30%
                    });
                }
            } catch (NumberFormatException e) {
                // userId가 잘못된 형식이면 무시
            }
        }
        
        // 점수 순으로 정렬하여 반환
        return activityScores.entrySet().stream()
                .sorted(Map.Entry.<ActivityEntity, Double>comparingByValue().reversed())
                .limit(safeLimit)
                .map(entry -> new SemanticSearchResult(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
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
}
