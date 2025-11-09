package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.repository.CalendarEventRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {
    
    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;
    
    // 사용자 캘린더 이벤트 목록 조회
    public List<CalendarEventEntity> getCalendarEvents(Long userId, String startDate, String endDate) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        
        if (startDate != null && !startDate.isEmpty()) {
            start = LocalDateTime.parse(startDate + "T00:00:00");
        }
        if (endDate != null && !endDate.isEmpty()) {
            end = LocalDateTime.parse(endDate + "T23:59:59");
        }
        
        return calendarEventRepository.findByUserIdAndDateRange(userId, start, end);
    }
    
    // 사용자 캘린더 이벤트 생성
    @Transactional
    public CalendarEventEntity createCalendarEvent(Long userId, Map<String, Object> eventData) {
        // 사용자 존재 확인
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId);
        }
        
        CalendarEventEntity event = new CalendarEventEntity();
        event.setUserId(userId);
        event.setTitle((String) eventData.get("title"));
        event.setDescription((String) eventData.get("description"));
        event.setLocation((String) eventData.get("location"));
        
        return calendarEventRepository.save(event);
    }
    
    // 특정 이벤트 조회
    public Optional<CalendarEventEntity> getCalendarEvent(Long eventId) {
        return calendarEventRepository.findById(eventId);
    }
    
    // 이벤트 업데이트
    @Transactional
    public Optional<CalendarEventEntity> updateCalendarEvent(Long eventId, Map<String, Object> eventData) {
        return calendarEventRepository.findById(eventId).map(event -> {
            event.setTitle((String) eventData.get("title"));
            event.setDescription((String) eventData.get("description"));
            event.setLocation((String) eventData.get("location"));
            
            String startTimeStr = (String) eventData.get("startTime");
            String endTimeStr = (String) eventData.get("endTime");
            
            if (startTimeStr != null) {
                event.setStartTime(LocalDateTime.parse(startTimeStr));
            }
            if (endTimeStr != null) {
                event.setEndTime(LocalDateTime.parse(endTimeStr));
            }
            
            Object activityIdObj = eventData.get("activityId");
            if (activityIdObj != null) {
                event.setActivityId(Long.valueOf(activityIdObj.toString()));
            }
            
            Object reminderObj = eventData.get("reminder");
            if (reminderObj != null) {
                event.setReminderMinutes(Integer.valueOf(reminderObj.toString()));
            }
            
            return calendarEventRepository.save(event);
        });
    }
    
    // 이벤트 삭제
    @Transactional
    public boolean deleteCalendarEvent(Long eventId) {
        if (calendarEventRepository.existsById(eventId)) {
            calendarEventRepository.deleteById(eventId);
            return true;
        }
        return false;
    }
    
    // 사용자의 다가오는 이벤트 조회
    public List<CalendarEventEntity> getUpcomingEvents(Long userId) {
        return calendarEventRepository.findUpcomingEventsByUserId(userId, LocalDateTime.now());
    }
    
    // 사용자의 특정 날짜 이벤트 조회
    public List<CalendarEventEntity> getEventsByDate(Long userId, String date) {
        LocalDateTime dateTime = LocalDateTime.parse(date + "T00:00:00");
        return calendarEventRepository.findByUserIdAndDate(userId, dateTime);
    }
    
    // 사용자의 특정 월 이벤트 조회
    public List<CalendarEventEntity> getEventsByMonth(Long userId, int year, int month) {
        return calendarEventRepository.findByUserIdAndMonth(userId, year, month);
    }
}
