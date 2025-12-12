package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityDateUpsertRequest;
import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.repository.ActivityDateRepository;
import com.mentoai.mentoai.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityDateService {

    private final ActivityRepository activityRepository;
    private final ActivityDateRepository activityDateRepository;

    @Transactional
    public void replaceActivityDates(Long activityId, List<ActivityDateUpsertRequest> dateRequests) {
        ActivityEntity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));

        List<ActivityDateEntity> dates = activity.getDates();
        if (dates == null) {
            dates = new ArrayList<>();
            activity.setDates(dates);
        } else {
            dates.clear(); // orphanRemoval=true: 기존 날짜는 제거됨
        }

        if (dateRequests != null) {
            for (ActivityDateUpsertRequest dateRequest : dateRequests) {
                ActivityDateEntity dateEntity = new ActivityDateEntity();
                dateEntity.setActivity(activity);
                dateEntity.setDateType(ActivityDateEntity.DateType.valueOf(dateRequest.dateType().toUpperCase()));
                dateEntity.setDateValue(dateRequest.dateValue());
                dates.add(dateEntity);
            }
        }

        activityRepository.save(activity);
    }
}

