package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.JobPostingResponse;
import com.mentoai.mentoai.controller.dto.JobPostingUpsertRequest;
import com.mentoai.mentoai.controller.dto.PagedJobPostingsResponse;
import com.mentoai.mentoai.controller.mapper.JobPostingMapper;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.service.JobPostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.List;

@RestController
@RequestMapping("/job-postings")
@Tag(name = "job-postings", description = "채용 공고 CRUD API")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @PostMapping
    @Operation(summary = "채용 공고 생성")
    public ResponseEntity<JobPostingResponse> createJobPosting(
            @Valid @RequestBody JobPostingUpsertRequest request) {
        JobPostingEntity created = jobPostingService.createJobPosting(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobPostingMapper.toResponse(created));
    }

    @PostMapping("/bulk")
    @Operation(summary = "채용 공고 대량 생성")
    public ResponseEntity<List<JobPostingResponse>> bulkCreateJobPostings(
            @Valid @RequestBody List<JobPostingUpsertRequest> requests) {
        List<JobPostingEntity> created = jobPostingService.createJobPostings(requests);
        List<JobPostingResponse> response = created.stream()
                .map(JobPostingMapper::toResponse)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{jobId}")
    @Operation(summary = "채용 공고 수정")
    public ResponseEntity<JobPostingResponse> updateJobPosting(
            @PathVariable Long jobId,
            @Valid @RequestBody JobPostingUpsertRequest request) {
        try {
            JobPostingEntity updated = jobPostingService.updateJobPosting(jobId, request);
            return ResponseEntity.ok(JobPostingMapper.toResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
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

    private OffsetDateTime parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }
}


