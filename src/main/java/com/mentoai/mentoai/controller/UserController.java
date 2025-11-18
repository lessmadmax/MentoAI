package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.CalendarEventResponse;
import com.mentoai.mentoai.controller.dto.CalendarEventUpsertRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.entity.CalendarEventEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserInterestEntity;
import com.mentoai.mentoai.repository.TagRepository;
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
    private final TagRepository tagRepository;

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

    @GetMapping("/{userId}")
    @Operation(summary = "사용자 조회", description = "특정 사용자의 정보를 반환합니다.")
    public ResponseEntity<UserEntity> getUser(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId) {
        Optional<UserEntity> user = userService.getUser(userId);
        return user.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{userId}/interests")
    @Operation(summary = "사용자 관심 태그 조회", description = "사용자의 관심 태그 목록을 반환합니다.")
    public ResponseEntity<List<com.mentoai.mentoai.controller.dto.UserInterestResponse>> listUserInterests(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId) {
        try {
            List<UserInterestEntity> interests = userInterestService.getUserInterests(userId);
            List<com.mentoai.mentoai.controller.dto.UserInterestResponse> responses = interests.stream()
                    .map(this::toUserInterestResponse)
                    .toList();
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    private com.mentoai.mentoai.controller.dto.UserInterestResponse toUserInterestResponse(UserInterestEntity entity) {
        String tagName = tagRepository.findById(entity.getTagId())
                .map(tag -> tag.getName())
                .orElse("");
        // score (1-5)를 weight (0.0-1.0)로 변환
        Double weight = entity.getScore() != null ? entity.getScore() / 5.0 : 0.7;
        return new com.mentoai.mentoai.controller.dto.UserInterestResponse(tagName, weight);
    }

    @PutMapping("/{userId}/interests")
    @Operation(summary = "사용자 관심 태그 설정/갱신", description = "사용자의 관심 태그를 설정/갱신합니다.")
    public ResponseEntity<List<com.mentoai.mentoai.controller.dto.UserInterestResponse>> upsertUserInterests(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> requestBody) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> interestsRaw = (List<Map<String, Object>>) requestBody.get("interests");
            if (interestsRaw == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // weight를 score로 변환 (weight 0.0-1.0 -> score 1-5)
            List<Map<String, Object>> interests = interestsRaw.stream()
                    .map(interest -> {
                        Map<String, Object> converted = new java.util.HashMap<>(interest);
                        Object weightObj = interest.get("weight");
                        if (weightObj != null) {
                            Double weight = weightObj instanceof Number 
                                    ? ((Number) weightObj).doubleValue() 
                                    : Double.parseDouble(weightObj.toString());
                            // weight를 score (1-5)로 변환
                            Integer score = (int) Math.round(weight * 5.0);
                            score = Math.max(1, Math.min(5, score)); // 1-5 범위로 제한
                            converted.put("score", score);
                        }
                        return converted;
                    })
                    .toList();
            
            List<UserInterestEntity> updatedInterests = userInterestService.upsertUserInterests(userId, interests);
            List<com.mentoai.mentoai.controller.dto.UserInterestResponse> responses = updatedInterests.stream()
                    .map(this::toUserInterestResponse)
                    .toList();
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{userId}/profile")
    @Operation(summary = "사용자 확장 프로필 조회", description = "확장 프로필 정보를 반환합니다.")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId) {
        UserProfileResponse profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{userId}/profile")
    @Operation(summary = "사용자 확장 프로필 설정/갱신", description = "확장 프로필을 생성/업데이트합니다.")
    public ResponseEntity<UserProfileResponse> upsertUserProfile(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId,
            @Valid @RequestBody UserProfileUpsertRequest request) {
        UserProfileResponse profile = userProfileService.upsertProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{userId}/calendar/events")
    @Operation(summary = "사용자의 캘린더 이벤트 목록", description = "사용자의 캘린더 이벤트를 조회합니다.")
    public ResponseEntity<List<CalendarEventResponse>> listCalendarEvents(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId,
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD)") @RequestParam(required = false) String endDate) {
        List<CalendarEventEntity> events = calendarEventService.getCalendarEvents(userId, startDate, endDate);
        return ResponseEntity.ok(events.stream().map(this::toCalendarEventResponse).toList());
    }

    @PostMapping("/{userId}/calendar/events")
    @Operation(summary = "캘린더 이벤트 생성", description = "사용자의 캘린더에 이벤트를 추가합니다.")
    public ResponseEntity<CalendarEventResponse> createCalendarEvent(
            @Parameter(description = "사용자 ID") @PathVariable("userId") Long userId,
            @Valid @RequestBody CalendarEventUpsertRequest request) {
        CalendarEventEntity createdEvent = calendarEventService.createCalendarEvent(userId, request);
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




