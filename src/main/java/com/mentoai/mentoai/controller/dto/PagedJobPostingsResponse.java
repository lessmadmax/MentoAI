package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record PagedJobPostingsResponse(
        int page,
        int size,
        long totalElements,
        List<JobPostingResponse> items
) {
}


