package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {
    
    private final ActivityRepository activityRepository;
    private final TagRepository tagRepository;
    private final NotificationService notificationService;
    
    public Page<ActivityEntity> getActivities(
            String query,
            ActivityType type,
            List<String> tagNames,
            Boolean isCampus,
            ActivityStatus status,
            int page,
            int size,
            String sort,
            String direction) {
        
        // 정렬 설정
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sortObj = Sort.by(sortDirection, sort);
        
        Pageable pageable = PageRequest.of(page, size, sortObj);
        
        // 태그가 있는 경우 복합 검색, 없는 경우 기본 검색
        if (tagNames != null && !tagNames.isEmpty()) {
            return activityRepository.findByComplexFilters(
                query, type, isCampus, status, tagNames, pageable);
        } else {
            return activityRepository.findByFilters(
                query, type, isCampus, status, pageable);
        }
    }
    
    @Transactional
    public ActivityEntity createActivity(ActivityEntity activity) {
        ActivityEntity savedActivity = activityRepository.save(activity);
        
        // 새로운 활동 알림 생성 (비동기)
        notificationService.createNewActivityNotification(savedActivity);
        
        return savedActivity;
    }
    
    public Optional<ActivityEntity> getActivity(Long id) {
        return activityRepository.findById(id);
    }
    
    @Transactional
    public Optional<ActivityEntity> updateActivity(Long id, ActivityEntity updatedActivity) {
        return activityRepository.findById(id)
            .map(existingActivity -> {
                existingActivity.setTitle(updatedActivity.getTitle());
                existingActivity.setContent(updatedActivity.getContent());
                existingActivity.setType(updatedActivity.getType());
                existingActivity.setOrganizer(updatedActivity.getOrganizer());
                existingActivity.setUrl(updatedActivity.getUrl());
                existingActivity.setIsCampus(updatedActivity.getIsCampus());
                existingActivity.setStatus(updatedActivity.getStatus());
                existingActivity.setStartDate(updatedActivity.getStartDate());
                existingActivity.setEndDate(updatedActivity.getEndDate());
                return activityRepository.save(existingActivity);
            });
    }
    
    @Transactional
    public boolean deleteActivity(Long id) {
        if (activityRepository.existsById(id)) {
            activityRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    public List<ActivityEntity> getActiveActivities() {
        return activityRepository.findByStatus(ActivityStatus.ACTIVE);
    }
    
    public List<ActivityEntity> getCampusActivities(Boolean isCampus) {
        return activityRepository.findByIsCampus(isCampus);
    }
}
