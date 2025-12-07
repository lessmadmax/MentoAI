package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.ActivityResponse;
import com.mentoai.mentoai.controller.dto.CalendarEventResponse;
import com.mentoai.mentoai.controller.dto.JobFitScoreResponse;
import com.mentoai.mentoai.controller.dto.JobPostingResponse;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.RoleFitResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.controller.mapper.JobPostingMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.security.AiApiKeyGuard;
import com.mentoai.mentoai.service.CalendarEventService;
import com.mentoai.mentoai.service.JobFitScoreService;
import com.mentoai.mentoai.service.JobPostingService;
import com.mentoai.mentoai.service.RoleFitService;
import com.mentoai.mentoai.service.UserProfileService;
import com.mentoai.mentoai.repository.ActivityRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "ai-data", description = "AI 전용 데이터 조회 API")
public class AiDataController {

    private static final int MAX_PAGE_SIZE = 200;

    private final CalendarEventService calendarEventService;
    private final ActivityRepository activityRepository;
    private final JobPostingService jobPostingService;
    private final JobFitScoreService jobFitScoreService;
    private final RoleFitService roleFitService;
    private final UserProfileService userProfileService;
    private final AiApiKeyGuard aiApiKeyGuard;

    @GetMapping("/calendar-events")
    @Operation(summary = "AI용 사용자 캘린더 이벤트 조회")
    public ResponseEntity<List<CalendarEventResponse>> getCalendarEvents(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        aiApiKeyGuard.verify(apiKey);
        List<CalendarEventResponse> responses = calendarEventService.getCalendarEvents(userId, startDate, endDate)
                .stream()
                .map(calendarEventService::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/activities")
    @Operation(summary = "AI용 활동 데이터 조회", description = "공모전/대회/스터디 등 활동 정보를 조회합니다.")
    public ResponseEntity<List<ActivityResponse>> getActivities(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean campusOnly,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        aiApiKeyGuard.verify(apiKey);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        ActivityType activityType = parseActivityType(type);
        ActivityStatus activityStatus = parseActivityStatus(status);
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ActivityEntity> page = activityRepository.findByFilters(
                StringUtils.hasText(query) ? query : null,
                activityType,
                campusOnly,
                activityStatus,
                pageable
        );

        List<ActivityResponse> responses = page.getContent().stream()
                .map(ActivityMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/job-postings")
    @Operation(summary = "AI용 채용 공고 조회")
    public ResponseEntity<List<JobPostingResponse>> getJobPostings(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String jobSector,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String targetRoleId,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        aiApiKeyGuard.verify(apiKey);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));

        var page = jobPostingService.searchJobPostings(
                query,
                company,
                jobSector,
                employmentType,
                targetRoleId,
                null,
                null,
                pageable
        );

        List<JobPostingResponse> responses = page.getContent().stream()
                .map(JobPostingMapper::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/job-fit-scores")
    @Operation(summary = "AI용 Job Fit Score 조회")
    public ResponseEntity<List<JobFitScoreResponse>> getJobFitScores(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam Long userId
    ) {
        aiApiKeyGuard.verify(apiKey);
        List<JobFitScoreResponse> responses = jobFitScoreService.getJobFitScores(userId);
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/role-fit")
    @Operation(summary = "AI용 Role Fit Score 계산")
    public ResponseEntity<RoleFitResponse> getRoleFit(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey,
            @RequestParam Long userId,
            @RequestParam(required = false) String targetRoleId
    ) {
        aiApiKeyGuard.verify(apiKey);
        String resolvedTargetRole = StringUtils.hasText(targetRoleId)
                ? targetRoleId
                : userProfileService.getProfile(userId).targetRoleId();

        if (!StringUtils.hasText(resolvedTargetRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetRoleId is required");
        }

        RoleFitResponse response = roleFitService.calculateRoleFit(
                userId,
                new RoleFitRequest(resolvedTargetRole, null)
        );
        return ResponseEntity.ok(response);
    }

    private ActivityType parseActivityType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ActivityType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unsupported activity type filter: {}", value);
            return null;
        }
    }

    private ActivityStatus parseActivityStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ActivityStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unsupported activity status filter: {}", value);
            return null;
        }
    }
}


