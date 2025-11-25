package com.mentoai.mentoai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoai.mentoai.controller.dto.JobFitScoreResponse;
import com.mentoai.mentoai.controller.dto.JobPostingResponse;
import com.mentoai.mentoai.controller.dto.JobPostingUpsertRequest;
import com.mentoai.mentoai.controller.dto.PagedJobPostingsResponse;
import com.mentoai.mentoai.controller.dto.RawJobPostingPayload;
import com.mentoai.mentoai.controller.mapper.JobPostingMapper;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.security.UserPrincipal;
import com.mentoai.mentoai.service.JobFitScoreService;
import com.mentoai.mentoai.service.JobPostingService;
import com.mentoai.mentoai.service.RawDataSchemaMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/job-postings")
@Tag(name = "job-postings", description = "채용 공고 CRUD API")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;
    private final JobFitScoreService jobFitScoreService;
    private final RawDataSchemaMapper rawDataSchemaMapper;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping
    @Operation(summary = "채용 공고 생성", description = "원시/정형 JSON을 자동 변환하여 공고를 생성합니다.")
    public ResponseEntity<JobPostingResponse> createJobPosting(@RequestBody JsonNode body) {
        try {
            JobPostingUpsertRequest request = convertJobPostingPayload(body);
            validate(request);
            JobPostingEntity created = jobPostingService.createJobPosting(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(JobPostingMapper.toResponse(created));
        } catch (ConstraintViolationException | IllegalArgumentException | JsonProcessingException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{jobId}/score")
    @Operation(summary = "공고 적합도 점수 계산", description = "사용자 프로필과 공고 요구사항을 비교해 점수를 계산합니다.")
    public ResponseEntity<JobFitScoreResponse> evaluateJobFit(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.id() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JobFitScoreResponse response = jobFitScoreService.evaluateJobFit(principal.id(), jobId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{jobId}/score")
    @Operation(summary = "공고 적합도 점수 조회", description = "이미 계산된 공고 점수를 조회합니다.")
    public ResponseEntity<JobFitScoreResponse> getJobFitScore(
            @PathVariable Long jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.id() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return jobFitScoreService.getLatestScore(principal.id(), jobId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/bulk")
    @Operation(summary = "채용 공고 대량 생성", description = "원시/정형 JSON 배열을 자동 변환하여 다건 생성합니다.")
    public ResponseEntity<List<JobPostingResponse>> bulkCreateJobPostings(
            @RequestBody JsonNode body) {
        try {
            if (body == null || !body.isArray()) {
                return ResponseEntity.badRequest().build();
            }
            List<JobPostingUpsertRequest> requests = new ArrayList<>();
            for (JsonNode node : body) {
                JobPostingUpsertRequest request = convertJobPostingPayload(node);
                validate(request);
                requests.add(request);
            }
            List<JobPostingEntity> created = jobPostingService.createJobPostings(requests);
            List<JobPostingResponse> response = created.stream()
                    .map(JobPostingMapper::toResponse)
                    .toList();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ConstraintViolationException | IllegalArgumentException | JsonProcessingException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{jobId}")
    @Operation(summary = "채용 공고 수정", description = "원시/정형 JSON을 자동 변환하여 공고를 수정합니다.")
    public ResponseEntity<JobPostingResponse> updateJobPosting(
            @PathVariable Long jobId,
            @RequestBody JsonNode body) {
        try {
            JobPostingUpsertRequest request = convertJobPostingPayload(body);
            validate(request);
            JobPostingEntity updated = jobPostingService.updateJobPosting(jobId, request);
            return ResponseEntity.ok(JobPostingMapper.toResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (ConstraintViolationException | JsonProcessingException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "채용 공고 단건 조회")
    public ResponseEntity<JobPostingResponse> getJobPosting(@PathVariable Long jobId) {
        return jobPostingService.getJobPosting(jobId)
                .map(JobPostingMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping
    @Operation(summary = "채용 공고 목록 조회")
    public ResponseEntity<PagedJobPostingsResponse> listJobPostings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String jobSector,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String targetRoleId,
            @RequestParam(required = false) String deadlineAfter,
            @RequestParam(required = false) String deadlineBefore,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<JobPostingEntity> result = jobPostingService.searchJobPostings(
                keyword,
                companyName,
                jobSector,
                employmentType,
                targetRoleId,
                parseDate(deadlineAfter),
                parseDate(deadlineBefore),
                pageable
        );

        List<JobPostingResponse> items = result.stream()
                .map(JobPostingMapper::toResponse)
                .toList();

        PagedJobPostingsResponse response = new PagedJobPostingsResponse(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                items
        );

        return ResponseEntity.ok(response);
    }

    private JobPostingUpsertRequest convertJobPostingPayload(JsonNode node) throws JsonProcessingException {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("요청 본문이 비어 있습니다.");
        }
        if (looksLikeRawJobPosting(node)) {
            RawJobPostingPayload raw = objectMapper.treeToValue(node, RawJobPostingPayload.class);
            return rawDataSchemaMapper.toJobPostingRequest(raw);
        }
        return objectMapper.treeToValue(node, JobPostingUpsertRequest.class);
    }

    private boolean looksLikeRawJobPosting(JsonNode node) {
        return node.hasNonNull("company") && !node.hasNonNull("companyName");
    }

    private <T> void validate(T payload) {
        var violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private OffsetDateTime parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }
}


