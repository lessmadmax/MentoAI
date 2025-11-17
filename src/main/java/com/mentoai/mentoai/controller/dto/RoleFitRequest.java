package com.mentoai.mentoai.controller.dto;

public record RoleFitRequest(
        String target,
        Integer topNImprovements
) {
    public int safeTopN() {
        return topNImprovements != null && topNImprovements > 0 ? topNImprovements : 5;
    }
}




