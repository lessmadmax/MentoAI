package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.NotificationEntity;
import com.mentoai.mentoai.entity.NotificationEntity.NotificationStatus;
import com.mentoai.mentoai.entity.NotificationEntity.NotificationType;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.NotificationRepository;
import com.mentoai.mentoai.repository.UserInterestRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final UserInterestRepository userInterestRepository;
    
    // 사용자 알림 목록 조회
    public Page<NotificationEntity> getUserNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByUserIdAndStatusOrderByReadAndCreatedAt(
                userId, NotificationStatus.ACTIVE, pageable);
    }
    
    // 읽지 않은 알림 개수 조회
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserIdAndStatus(userId, NotificationStatus.ACTIVE);
    }
    
    // 알림 읽음 처리
    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        Optional<NotificationEntity> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent() && notification.get().getUserId().equals(userId)) {
            NotificationEntity notif = notification.get();
            notif.setIsRead(true);
            notif.setReadAt(LocalDateTime.now());
            notificationRepository.save(notif);
            return true;
        }
        return false;
    }
    
    // 모든 알림 읽음 처리
    @Transactional
    public void markAllAsRead(Long userId) {
        List<NotificationEntity> unreadNotifications = notificationRepository
                .findByUserIdAndStatusOrderByReadAndCreatedAt(userId, NotificationStatus.ACTIVE, PageRequest.of(0, 1000))
                .getContent();
        
        LocalDateTime now = LocalDateTime.now();
        for (NotificationEntity notification : unreadNotifications) {
            if (!notification.getIsRead()) {
                notification.setIsRead(true);
                notification.setReadAt(now);
            }
        }
        notificationRepository.saveAll(unreadNotifications);
    }
    
    // 알림 삭제
    @Transactional
    public boolean deleteNotification(Long notificationId, Long userId) {
        Optional<NotificationEntity> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent() && notification.get().getUserId().equals(userId)) {
            NotificationEntity notif = notification.get();
            notif.setStatus(NotificationStatus.DELETED);
            notificationRepository.save(notif);
            return true;
        }
        return false;
    }
    
    // 새로운 활동 알림 생성
    @Async
    @Transactional
    public CompletableFuture<Void> createNewActivityNotification(ActivityEntity activity) {
        log.info("Creating new activity notification: {}", activity.getTitle());
        
        // 모든 사용자에게 기본 알림
        List<Long> allUserIds = userRepository.findAll().stream()
                .map(user -> user.getId())
                .toList();
        
        for (Long userId : allUserIds) {
            NotificationEntity notification = new NotificationEntity();
            notification.setUserId(userId);
            notification.setTitle("A new activity has been posted");
            notification.setMessage(activity.getTitle());
            notification.setType(NotificationType.NEW_ACTIVITY);
            notification.setStatus(NotificationStatus.ACTIVE);
            notification.setActivityId(activity.getId());
            
            notificationRepository.save(notification);
        }
        
        // Additional notifications for users whose interests match
        createInterestMatchNotifications(activity);
        
        return CompletableFuture.completedFuture(null);
    }
    
    // 관심사 매칭 알림 생성
    @Async
    @Transactional
    public CompletableFuture<Void> createInterestMatchNotifications(ActivityEntity activity) {
        if (activity.getActivityTags() == null || activity.getActivityTags().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // 활동의 태그 ID들 추출
        List<Long> activityTagIds = activity.getActivityTags().stream()
                .map(activityTag -> activityTag.getTag().getId())
                .toList();
        
        // Find users whose interests match
        List<UserInterestEntity> matchingInterests = userInterestRepository.findAll().stream()
                .filter(interest -> activityTagIds.contains(interest.getTagId()))
                .filter(interest -> interest.getScore() >= 3) // 점수 3 이상만
                .toList();
        
        for (UserInterestEntity interest : matchingInterests) {
            NotificationEntity notification = new NotificationEntity();
            notification.setUserId(interest.getUserId());
            notification.setTitle("An activity matches your interests!");
            notification.setMessage(activity.getTitle() + " - This activity matches your interests.");
            notification.setType(NotificationType.INTEREST_MATCH);
            notification.setStatus(NotificationStatus.ACTIVE);
            notification.setActivityId(activity.getId());
            
            notificationRepository.save(notification);
        }
        
        log.info("Interest-match notifications created for {} users", matchingInterests.size());
        return CompletableFuture.completedFuture(null);
    }
    
    // 마감 임박 알림 생성
    @Async
    @Transactional
    public CompletableFuture<Void> createDeadlineReminderNotifications() {
        log.info("Starting deadline reminder notifications");
        
        LocalDateTime threeDaysFromNow = LocalDateTime.now().plusDays(3);
        LocalDateTime now = LocalDateTime.now();
        
        // Find activities that will close within 3 days
        List<ActivityEntity> upcomingDeadlines = activityRepository.findAll().stream()
                .map(activity -> Map.entry(activity, extractApplyEndDate(activity)))
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> entry.getValue().isAfter(now))
                .filter(entry -> entry.getValue().isBefore(threeDaysFromNow))
                .map(Map.Entry::getKey)
                .toList();
        
        for (ActivityEntity activity : upcomingDeadlines) {
            // Check if reminders were already sent
            List<NotificationEntity> existingReminders = notificationRepository
                    .findDeadlineRemindersByActivityId(activity.getId());
            
            if (existingReminders.isEmpty()) {
                // Send deadline reminders to all users
                List<Long> allUserIds = userRepository.findAll().stream()
                        .map(user -> user.getId())
                        .toList();
                
                for (Long userId : allUserIds) {
                    NotificationEntity notification = new NotificationEntity();
                    notification.setUserId(userId);
                    notification.setTitle("Deadline reminder");
                    notification.setMessage(activity.getTitle() + " - 3 days left until the deadline.");
                    notification.setType(NotificationType.DEADLINE_REMINDER);
                    notification.setStatus(NotificationStatus.ACTIVE);
                    notification.setActivityId(activity.getId());
                    
                    notificationRepository.save(notification);
                }
            }
        }
        
        log.info("Deadline reminders created for {} activities", upcomingDeadlines.size());
        return CompletableFuture.completedFuture(null);
    }

    private LocalDateTime extractApplyEndDate(ActivityEntity activity) {
        if (activity.getDates() == null || activity.getDates().isEmpty()) {
            return null;
        }
        return activity.getDates().stream()
                .filter(date -> date.getDateType() == ActivityDateEntity.DateType.APPLY_END)
                .map(ActivityDateEntity::getDateValue)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }
    
    // 추천 활동 알림 생성
    @Async
    @Transactional
    public CompletableFuture<Void> createRecommendationNotifications(Long userId, List<ActivityEntity> recommendations) {
        if (recommendations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setTitle("New recommended activities are available!");
        notification.setMessage("Check out " + recommendations.size() + " personalized recommendations.");
        notification.setType(NotificationType.RECOMMENDATION);
        notification.setStatus(NotificationStatus.ACTIVE);
        
        notificationRepository.save(notification);
        
        log.info("Recommendation notification created: user={}, recommendations={}", userId, recommendations.size());
        return CompletableFuture.completedFuture(null);
    }
    
    // 시스템 공지 생성
    @Transactional
    public void createSystemAnnouncement(String title, String message) {
        List<Long> allUserIds = userRepository.findAll().stream()
                .map(user -> user.getId())
                .toList();
        
        for (Long userId : allUserIds) {
            NotificationEntity notification = new NotificationEntity();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setMessage(message);
            notification.setType(NotificationType.SYSTEM_ANNOUNCEMENT);
            notification.setStatus(NotificationStatus.ACTIVE);
            
            notificationRepository.save(notification);
        }
        
        log.info("System announcement created and sent to {} users", allUserIds.size());
    }
    
    // 오래된 알림 정리
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        List<NotificationEntity> oldNotifications = notificationRepository.findOldNotifications(cutoffDate);
        
        for (NotificationEntity notification : oldNotifications) {
            notification.setStatus(NotificationStatus.ARCHIVED);
        }
        
        notificationRepository.saveAll(oldNotifications);
        log.info("오래된 알림 정리 완료: {}개 알림 보관 처리", oldNotifications.size());
    }
}



