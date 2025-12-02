package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.CalendarEventUpsertRequest;
import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.entity.CalendarEventType;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.CalendarEventRepository;
import com.mentoai.mentoai.repository.JobPostingRepository;
import com.mentoai.mentoai.repository.RecommendChatLogRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final JobPostingRepository jobPostingRepository;
    private final RecommendChatLogRepository recommendChatLogRepository;

    public List<CalendarEventEntity> getCalendarEvents(Long userId, String startDate, String endDate) {
        LocalDateTime start = parseBoundary(startDate, true);
        LocalDateTime end = parseBoundary(endDate, false);
        if (start == null && end == null) {
            return calendarEventRepository.findByUserId(userId);
        }
        return calendarEventRepository.findByUserIdAndDateRange(userId, start, end);
    }

    @Transactional
    public CalendarEventEntity createCalendarEvent(Long userId, CalendarEventUpsertRequest request) {
        assertUserExists(userId);
        validateReference(userId, request);
        Optional<CalendarEventEntity> existingEvent = findExistingEvent(userId, request);
        if (existingEvent.isPresent()) {
            return existingEvent.get();
        }

        CalendarEventEntity event = new CalendarEventEntity();
        event.setUserId(userId);
        apply(event, request);
        return calendarEventRepository.save(event);
    }

    public Optional<CalendarEventEntity> getCalendarEvent(Long userId, Long eventId) {
        return calendarEventRepository.findById(eventId)
                .filter(event -> event.getUserId().equals(userId));
    }

    @Transactional
    public Optional<CalendarEventEntity> updateCalendarEvent(Long userId,
                                                             Long eventId,
                                                             CalendarEventUpsertRequest request) {
        validateReference(userId, request);
        return calendarEventRepository.findById(eventId)
                .filter(event -> event.getUserId().equals(userId))
                .map(entity -> {
                    apply(entity, request);
                    return calendarEventRepository.save(entity);
                });
    }

    @Transactional
    public boolean deleteCalendarEvent(Long userId, Long eventId) {
        return calendarEventRepository.findById(eventId)
                .filter(event -> event.getUserId().equals(userId))
                .map(event -> {
                    calendarEventRepository.delete(event);
                    return true;
                })
                .orElse(false);
    }

    public List<CalendarEventEntity> getUpcomingEvents(Long userId) {
        return calendarEventRepository.findUpcomingEventsByUserId(userId, LocalDateTime.now());
    }

    public List<CalendarEventEntity> getEventsByDate(Long userId, String date) {
        LocalDateTime dateTime = parseBoundary(date, true);
        return calendarEventRepository.findByUserIdAndDate(userId, dateTime);
    }

    public List<CalendarEventEntity> getEventsByMonth(Long userId, int year, int month) {
        return calendarEventRepository.findByUserIdAndMonth(userId, year, month);
    }

    private void apply(CalendarEventEntity entity, CalendarEventUpsertRequest request) {
        entity.setEventType(request.eventType());
        if (request.eventType() == CalendarEventType.ACTIVITY) {
            entity.setActivityId(request.activityId());
            entity.setJobPostingId(null);
        } else if (request.eventType() == CalendarEventType.JOB_POSTING) {
            entity.setJobPostingId(request.jobPostingId());
            entity.setActivityId(null);
        } else {
            entity.setActivityId(null);
            entity.setJobPostingId(null);
        }
        entity.setRecommendLogId(request.recommendLogId());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setAlertMinutes(request.alertMinutes());
    }

    private void assertUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
    }

    private void validateReference(Long userId, CalendarEventUpsertRequest request) {
        if (request.eventType() == null) {
            throw new IllegalArgumentException("eventType is required");
        }

        switch (request.eventType()) {
            case ACTIVITY -> {
                if (request.activityId() == null) {
                    throw new IllegalArgumentException("activityId is required for ACTIVITY events");
                }
                if (!activityRepository.existsById(request.activityId())) {
                    throw new IllegalArgumentException("활동을 찾을 수 없습니다: " + request.activityId());
                }
            }
            case JOB_POSTING -> {
                if (request.jobPostingId() == null) {
                    throw new IllegalArgumentException("jobPostingId is required for JOB_POSTING events");
                }
                if (!jobPostingRepository.existsById(request.jobPostingId())) {
                    throw new IllegalArgumentException("채용 공고를 찾을 수 없습니다: " + request.jobPostingId());
                }
            }
            case CUSTOM -> {
                // no-op
            }
        }

        if (request.recommendLogId() != null) {
            recommendChatLogRepository.findById(request.recommendLogId())
                    .filter(log -> log.getUserId().equals(userId))
                    .orElseThrow(() -> new IllegalArgumentException("추천 로그를 찾을 수 없습니다: " + request.recommendLogId()));
        }
    }

    private Optional<CalendarEventEntity> findExistingEvent(Long userId, CalendarEventUpsertRequest request) {
        if (request.eventType() == CalendarEventType.ACTIVITY && request.activityId() != null) {
            return calendarEventRepository.findByUserIdAndEventTypeAndActivityId(
                    userId, CalendarEventType.ACTIVITY, request.activityId());
        }
        if (request.eventType() == CalendarEventType.JOB_POSTING && request.jobPostingId() != null) {
            return calendarEventRepository.findByUserIdAndEventTypeAndJobPostingId(
                    userId, CalendarEventType.JOB_POSTING, request.jobPostingId());
        }
        return Optional.empty();
    }

    private LocalDateTime parseBoundary(String date, boolean isStart) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        return isStart ? LocalDateTime.parse(date + "T00:00:00") : LocalDateTime.parse(date + "T23:59:59");
    }
}
