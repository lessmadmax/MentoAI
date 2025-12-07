package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.security.AiApiKeyGuard;
import com.mentoai.mentoai.service.S3DataIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/ingest")
@Tag(name = "ai-ingest", description = "AI 데이터 적재 트리거")
@RequiredArgsConstructor
public class AiIngestionController {

    private final AiApiKeyGuard aiApiKeyGuard;
    private final S3DataIngestionService s3DataIngestionService;

    @PostMapping("/contests")
    @Operation(summary = "S3 → 공모전/대회 데이터 적재")
    public ResponseEntity<S3DataIngestionService.IngestionResult> ingestContests(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey
    ) {
        aiApiKeyGuard.verify(apiKey);
        return ResponseEntity.ok(s3DataIngestionService.ingestContestActivities());
    }

    @PostMapping("/job-postings")
    @Operation(summary = "S3 → 채용 공고 데이터 적재")
    public ResponseEntity<S3DataIngestionService.IngestionResult> ingestJobPostings(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey
    ) {
        aiApiKeyGuard.verify(apiKey);
        return ResponseEntity.ok(s3DataIngestionService.ingestJobPostings());
    }

    @PostMapping("/all")
    @Operation(summary = "S3 → 전체 데이터 적재")
    public ResponseEntity<S3DataIngestionService.CombinedIngestionResult> ingestAll(
            @RequestHeader(value = "X-AI-API-KEY", required = false) String apiKey
    ) {
        aiApiKeyGuard.verify(apiKey);
        return ResponseEntity.ok(s3DataIngestionService.ingestAll());
    }
}


