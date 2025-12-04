package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.JobPostingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobPostingRepository extends JpaRepository<JobPostingEntity, Long>,
        JpaSpecificationExecutor<JobPostingEntity> {
}

