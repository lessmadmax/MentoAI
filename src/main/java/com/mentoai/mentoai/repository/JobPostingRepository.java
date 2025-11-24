package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.JobPostingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPostingEntity, Long> {
}

