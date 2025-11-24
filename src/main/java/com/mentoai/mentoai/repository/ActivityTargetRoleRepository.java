package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ActivityTargetRoleEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityTargetRoleRepository extends JpaRepository<ActivityTargetRoleEntity, Long> {

    List<ActivityTargetRoleEntity> findByActivityId(Long activityId);

    List<ActivityTargetRoleEntity> findByTargetRoleIdOrderBySimilarityScoreDesc(String targetRoleId, Pageable pageable);

    void deleteByActivityId(Long activityId);

    Optional<ActivityTargetRoleEntity> findByActivityIdAndTargetRoleId(Long activityId, String targetRoleId);
}

