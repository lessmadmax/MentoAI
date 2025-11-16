package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.CalendarEventUpsertRequest;
import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.repository.CalendarEventRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;

    public List<CalendarEventEntity> getCalendarEvents(Long userId, String startDate, String endDate) {
        LocalDateTime start = parseBoundary(startDate, true);
        LocalDateTime end = parseBoundary(endDate, false);
        return calendarEventRepository.findByUserIdAndDateRange(userId, start, end);
    }

    @Transactional
    public CalendarEventEntity createCalendarEvent(Long userId, CalendarEventUpsertRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        CalendarEventEntity event = new CalendarEventEntity();
        event.setUserId(userId);
        apply(event, request);
        return calendarEventRepository.save(event);
    }

    public Optional<CalendarEventEntity> getCalendarEvent(Long eventId) {
        return calendarEventRepository.findById(eventId);
    }

    @Transactional
    public Optional<CalendarEventEntity> updateCalendarEvent(Long eventId, CalendarEventUpsertRequest request) {
        return calendarEventRepository.findById(eventId).map(entity -> {
            apply(entity, request);
            return calendarEventRepository.save(entity);
        });
    }

    @Transactional
    public boolean deleteCalendarEvent(Long eventId) {
        if (calendarEventRepository.existsById(eventId)) {
            calendarEventRepository.deleteById(eventId);
            return true;
        }
        return false;
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
        entity.setActivityId(request.activityId());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setAlertMinutes(request.alertMinutes());
    }

    private LocalDateTime parseBoundary(String date, boolean isStart) {
        if (date == null || date.isEmpty()) {
            return null;
        }
        return isStart ? LocalDateTime.parse(date + "T00:00:00") : LocalDateTime.parse(date + "T23:59:59");
    }
}
