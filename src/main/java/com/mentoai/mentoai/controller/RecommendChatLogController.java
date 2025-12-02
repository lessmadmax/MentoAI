package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.RecommendChatLogDetailResponse;
import com.mentoai.mentoai.controller.dto.RecommendChatLogSummaryResponse;
import com.mentoai.mentoai.entity.RecommendChatLogEntity;
import com.mentoai.mentoai.security.UserPrincipal;
import com.mentoai.mentoai.service.RecommendChatLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommend/chats")
@Tag(name = "recommend", description = "추천 대화 로그 API")
@RequiredArgsConstructor
public class RecommendChatLogController {

    private final RecommendChatLogService recommendChatLogService;

    @GetMapping
    @Operation(summary = "추천 대화 로그 목록 조회")
    public ResponseEntity<List<RecommendChatLogSummaryResponse>> listLogs(
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<RecommendChatLogEntity> logs = recommendChatLogService.getLogsForUser(principal.id());
        List<RecommendChatLogSummaryResponse> response = logs.stream()
                .map(log -> new RecommendChatLogSummaryResponse(
                        log.getId(),
                        log.getTargetRoleId(),
                        log.getUserQuery(),
                        log.getCreatedAt(),
                        log.getUpdatedAt()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{logId}")
    @Operation(summary = "추천 대화 로그 상세 조회")
    public ResponseEntity<RecommendChatLogDetailResponse> getLog(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long logId,
            @RequestParam(name = "includePayload", defaultValue = "false") boolean includePayload) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return recommendChatLogService.getLog(principal.id(), logId)
                .map(log -> ResponseEntity.ok(new RecommendChatLogDetailResponse(
                        log.getId(),
                        log.getTargetRoleId(),
                        log.getUserQuery(),
                        log.getRagPrompt(),
                        log.getGeminiResponse(),
                        includePayload ? recommendChatLogService.parseJson(log.getRequestPayload()) : null,
                        includePayload ? recommendChatLogService.parseJson(log.getResponsePayload()) : null,
                        log.getModelName(),
                        log.getCreatedAt(),
                        log.getUpdatedAt()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

