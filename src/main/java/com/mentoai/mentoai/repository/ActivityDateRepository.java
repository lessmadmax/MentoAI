package com.mentoai.mentoai.repository;

import com.mentoai.mentoai.entity.ActivityDateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityDateRepository extends JpaRepository<ActivityDateEntity, Long> {

    @Modifying
    @Query("delete from ActivityDateEntity ad where ad.activity.id = :activityId")
    void deleteByActivityId(@Param("activityId") Long activityId);
}

