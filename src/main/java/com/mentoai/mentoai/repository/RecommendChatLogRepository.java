package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.RecommendChatLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendChatLogRepository extends JpaRepository<RecommendChatLogEntity, Long> {

    List<RecommendChatLogEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}

