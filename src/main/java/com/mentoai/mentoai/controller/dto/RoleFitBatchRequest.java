package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record RoleFitBatchRequest(
        List<String> targets,
        Integer topNImprovements
) {
    public int safeTopN() {
        return topNImprovements != null && topNImprovements > 0 ? topNImprovements : 5;
    }
}


