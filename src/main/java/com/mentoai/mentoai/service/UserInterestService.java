package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.TagRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInterestService {
    
    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    
    // 사용자 관심사 목록 조회
    public List<UserInterestEntity> getUserInterests(Long userId) {
        return userInterestRepository.findByUserIdOrderByScoreDesc(userId);
    }
    
    // 사용자 관심사 업데이트 (upsert)
    @Transactional
    public List<UserInterestEntity> upsertUserInterests(Long userId, List<Map<String, Object>> interests) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        for (Map<String, Object> interest : interests) {
            String tagName = (String) interest.get("tagName");
            Integer score = (Integer) interest.get("score");
            
            if (tagName == null || score == null) {
                throw new IllegalArgumentException("tagName과 score는 필수입니다.");
            }
            
            if (score < 1 || score > 5) {
                throw new IllegalArgumentException("점수는 1-5 사이여야 합니다.");
            }
            
            // 태그 존재 확인 및 조회
            TagEntity tag = tagRepository.findByName(tagName)
                .orElseThrow(() -> new IllegalArgumentException("태그를 찾을 수 없습니다: " + tagName));
            
            // 기존 관심사 확인
            Optional<UserInterestEntity> existingInterest = 
                userInterestRepository.findByUserIdAndTagId(userId, tag.getId());
            
            if (existingInterest.isPresent()) {
                // 기존 관심사 업데이트
                UserInterestEntity userInterest = existingInterest.get();
                userInterest.setScore(score);
                userInterestRepository.save(userInterest);
            } else {
                // 새로운 관심사 생성
                UserInterestEntity newInterest = new UserInterestEntity();
                newInterest.setUserId(userId);
                newInterest.setTagId(tag.getId());
                newInterest.setScore(score);
                userInterestRepository.save(newInterest);
            }
        }
        
        return getUserInterests(userId);
    }
    
    // 사용자 관심사 삭제
    @Transactional
    public boolean deleteUserInterest(Long userId, Long tagId) {
        if (userInterestRepository.existsByUserIdAndTagId(userId, tagId)) {
            userInterestRepository.deleteByUserIdAndTagId(userId, tagId);
            return true;
        }
        return false;
    }
    
    // 특정 점수 이상의 관심사 조회
    public List<UserInterestEntity> getHighScoreInterests(Long userId, Integer minScore) {
        return userInterestRepository.findByUserIdAndScoreGreaterThanEqual(userId, minScore);
    }
    
    // 사용자 관심사 존재 여부 확인
    public boolean hasInterest(Long userId, Long tagId) {
        return userInterestRepository.existsByUserIdAndTagId(userId, tagId);
    }
}
