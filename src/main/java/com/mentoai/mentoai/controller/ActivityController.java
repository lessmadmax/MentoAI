package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/activities")
@Tag(name = "activities", description = "공모전/채용/교내행사 등 활동 리소스")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    @Operation(summary = "활동 목록 조회", description = "필터/정렬을 포함한 활동 목록을 반환합니다.")
    public ResponseEntity<Page<ActivityEntity>> listActivities(
            @Parameter(description = "제목/내용 전체 검색어") @RequestParam(required = false) String q,
            @Parameter(description = "활동 유형 필터") @RequestParam(required = false) ActivityType type,
            @Parameter(description = "태그 이름(복수 지정 시 콤마 구분)") @RequestParam(required = false) String tag,
            @Parameter(description = "교내 활동 여부") @RequestParam(required = false) Boolean isCampus,
            @Parameter(description = "활동 상태") @RequestParam(required = false) ActivityStatus status,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "정렬 기준") @RequestParam(defaultValue = "createdAt") String sort,
            @Parameter(description = "정렬 방향") @RequestParam(defaultValue = "desc") String direction) {
        
        // 태그 문자열을 리스트로 변환
        List<String> tagNames = null;
        if (tag != null && !tag.trim().isEmpty()) {
            tagNames = Arrays.asList(tag.split(","));
        }
        
        Page<ActivityEntity> activities = activityService.getActivities(
            q, type, tagNames, isCampus, status, page, size, sort, direction);
        
        return ResponseEntity.ok(activities);
    }

    @PostMapping
    @Operation(summary = "활동 생성", description = "새로운 활동을 생성합니다.")
    public ResponseEntity<ActivityEntity> createActivity(@RequestBody ActivityEntity activity) {
        ActivityEntity createdActivity = activityService.createActivity(activity);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdActivity);
    }

    @GetMapping("/{id}")
    @Operation(summary = "활동 상세 조회", description = "특정 활동의 상세 정보를 반환합니다.")
    public ResponseEntity<ActivityEntity> getActivity(
            @Parameter(description = "활동 ID") @PathVariable Long id) {
        Optional<ActivityEntity> activity = activityService.getActivity(id);
        return activity.map(ResponseEntity::ok)
                     .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "활동 수정", description = "기존 활동 정보를 수정합니다.")
    public ResponseEntity<ActivityEntity> updateActivity(
            @Parameter(description = "활동 ID") @PathVariable Long id,
            @RequestBody ActivityEntity activity) {
        Optional<ActivityEntity> updatedActivity = activityService.updateActivity(id, activity);
        return updatedActivity.map(ResponseEntity::ok)
                             .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "활동 삭제", description = "활동을 삭제합니다.")
    public ResponseEntity<Void> deleteActivity(
            @Parameter(description = "활동 ID") @PathVariable Long id) {
        boolean deleted = activityService.deleteActivity(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "활동 첨부파일 목록", description = "특정 활동의 첨부파일 목록을 반환합니다.")
    public ResponseEntity<List<Map<String, Object>>> listAttachments(
            @Parameter(description = "활동 ID") @PathVariable Long id) {
        // TODO: 첨부파일 서비스 구현 후 연결
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "활동 첨부파일 추가", description = "활동에 첨부파일을 추가합니다.")
    public ResponseEntity<Map<String, Object>> addAttachment(
            @Parameter(description = "활동 ID") @PathVariable Long id,
            @RequestBody Map<String, Object> attachment) {
        // TODO: 첨부파일 서비스 구현 후 연결
        return ResponseEntity.ok(attachment);
    }
}
