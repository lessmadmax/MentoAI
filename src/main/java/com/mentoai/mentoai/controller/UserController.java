package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.CalendarEventResponse;
import com.mentoai.mentoai.controller.dto.CalendarEventUpsertRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.service.CalendarEventService;
import com.mentoai.mentoai.service.UserInterestService;
import com.mentoai.mentoai.service.UserProfileService;
import com.mentoai.mentoai.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    private final UserProfileService userProfileService;
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

    @GetMapping("/{id}/profile")
    @Operation(summary = "사용자 프로필 조회", description = "확장 프로필 정보를 반환합니다.")
    public ResponseEntity<UserProfileResponse> getProfile(
            @Parameter(description = "사용자 ID") @PathVariable Long id) {
        UserProfileResponse profile = userProfileService.getProfile(id);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{id}/profile")
    @Operation(summary = "사용자 프로필 갱신", description = "확장 프로필을 생성/업데이트합니다.")
    public ResponseEntity<UserProfileResponse> upsertProfile(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @Valid @RequestBody UserProfileUpsertRequest request) {
        UserProfileResponse profile = userProfileService.upsertProfile(id, request);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{id}/calendar/events")
    @Operation(summary = "사용자 캘린더 이벤트 목록", description = "사용자의 캘린더 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> listCalendarEvents(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD)") @RequestParam(required = false) String endDate) {
        List<CalendarEventEntity> events = calendarEventService.getCalendarEvents(id, startDate, endDate);
        return ResponseEntity.ok(events.stream().map(this::toCalendarEventResponse).toList());
    }

    @PostMapping("/{id}/calendar/events")
    @Operation(summary = "캘린더 이벤트 생성", description = "사용자의 캘린더에 이벤트를 추가합니다.")
    public ResponseEntity<CalendarEventResponse> createCalendarEvent(
            @Parameter(description = "사용자 ID") @PathVariable Long id,
            @Valid @RequestBody CalendarEventUpsertRequest request) {
        CalendarEventEntity createdEvent = calendarEventService.createCalendarEvent(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCalendarEventResponse(createdEvent));
    }

    private CalendarEventResponse toCalendarEventResponse(CalendarEventEntity entity) {
        return new CalendarEventResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getActivityId(),
            entity.getStartAt(),
            entity.getEndAt(),
            entity.getAlertMinutes(),
            entity.getCreatedAt()
        );
    }
}




