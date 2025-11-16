package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record SemanticSearchResponse(
        List<Double> queryEmbedding,
        List<ResultItem> results
) {
    public record ResultItem(
            ActivityResponse activity,
            double score
    ) {
    }
}


