package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.service.UserService;
import com.mentoai.mentoai.service.UserInterestService;
import com.mentoai.mentoai.service.CalendarEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "사용자/관심사/캘린더")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserInterestService userInterestService;
    private final CalendarEventService calendarEventService;

    @PostMapping
    @Operation(summary = "사용자 생성", description = "새로운 사용자를 생성합니다.")
    public ResponseEntity<UserEntity> createUser(@RequestBody UserEntity user) {
        try {
            UserEntity createdUser = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 조회", description = "특정 사용자의 정보를 반환합니다.")
    public ResponseEntity<UserEntity> getUser(
            @Parameter(description = "사용자 ID") @PathVariable Long id) {
        Optional<UserEntity> user = userService.getUser(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/interests")
    @Operation(summary = "사용자 관심사 목록", description = "사용자의 관심사 목록을 반환합니다.")
    public ResponseEntity<List<UserInterestEntity>> listUserInterests(
            @Parameter(description = "사용자 ID") @PathVariable Long id) {
        try {
            List<UserInterestEntity> interests = userInterestService.getUserInterests(id);
            return ResponseEntity.ok(interests);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/interests")
    @Operation(summary = "사용자 관심사 업데이트", description = "사용자의 관심사를 업데이트합니다.")
    public ResponseEntity<List<UserInterestEntity>> upsertUserInterests(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @RequestBody List<Map<String, Object>> interests) {
        try {
            List<UserInterestEntity> updatedInterests = userInterestService.upsertUserInterests(id, interests);
            return ResponseEntity.ok(updatedInterests);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/calendar")
    @Operation(summary = "사용자 캘린더 이벤트 목록", description = "사용자의 캘린더 이벤트 목록을 반환합니다.")
    public ResponseEntity<List<CalendarEventEntity>> listCalendarEvents(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @Parameter(description = "시작 날짜") @RequestParam(required = false) String startDate,
            @Parameter(description = "종료 날짜") @RequestParam(required = false) String endDate) {
        try {
            List<CalendarEventEntity> events = calendarEventService.getCalendarEvents(id, startDate, endDate);
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/calendar")
    @Operation(summary = "캘린더 이벤트 생성", description = "사용자의 캘린더에 이벤트를 추가합니다.")
    public ResponseEntity<CalendarEventEntity> createCalendarEvent(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @RequestBody Map<String, Object> event) {
        try {
            CalendarEventEntity createdEvent = calendarEventService.createCalendarEvent(id, event);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
