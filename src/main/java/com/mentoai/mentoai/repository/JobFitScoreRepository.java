package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.JobFitScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobFitScoreRepository extends JpaRepository<JobFitScoreEntity, Long> {

    Optional<JobFitScoreEntity> findByUserIdAndJobId(Long userId, Long jobId);
}

