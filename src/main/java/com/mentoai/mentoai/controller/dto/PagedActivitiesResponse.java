package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record PagedActivitiesResponse(
        int page,
        int size,
        long totalElements,
        List<ActivityResponse> items
) {
}


