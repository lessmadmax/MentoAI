package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.ActivityTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityTagRepository extends JpaRepository<ActivityTagEntity, ActivityTagId> {

    @Modifying
    @Query("delete from ActivityTagEntity at where at.id.activityId = :activityId")
    void deleteByActivityId(@Param("activityId") Long activityId);
}

