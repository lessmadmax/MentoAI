package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.UserInterestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInterestRepository extends JpaRepository<UserInterestEntity, Long> {
    
    // 특정 사용자의 모든 관심사 조회
    List<UserInterestEntity> findByUserId(Long userId);
    
    // 특정 사용자의 특정 태그 관심사 조회
    Optional<UserInterestEntity> findByUserIdAndTagId(Long userId, Long tagId);
    
    // 사용자 관심사 존재 여부 확인
    boolean existsByUserIdAndTagId(Long userId, Long tagId);
    
    // 사용자 관심사 삭제
    void deleteByUserIdAndTagId(Long userId, Long tagId);
    
    // 사용자의 관심사 점수별 정렬 조회
    @Query("SELECT ui FROM UserInterestEntity ui WHERE ui.userId = :userId ORDER BY ui.score DESC")
    List<UserInterestEntity> findByUserIdOrderByScoreDesc(@Param("userId") Long userId);
    
    // 특정 점수 이상의 관심사 조회
    List<UserInterestEntity> findByUserIdAndScoreGreaterThanEqual(Long userId, Integer minScore);
}
