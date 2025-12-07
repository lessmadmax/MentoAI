package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record JobRecommendResponse(
        List<JobItem> items
) {

    public record JobItem(
            JobPostingResponse jobPosting,
            double score,
            String reason,
            Double similarityScore,
            Double skillOverlap
    ) {
    }
}


