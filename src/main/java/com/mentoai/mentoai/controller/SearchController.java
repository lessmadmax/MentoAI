package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.SemanticSearchResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "activities", description = "의미 기반 검색")
public class SearchController {

    private final RecommendService recommendService;

    @GetMapping("/search")
    @Operation(summary = "의미 기반 검색", description = "쿼리를 임베딩하고 유사 활동을 반환합니다.")
    public ResponseEntity<SemanticSearchResponse> semanticSearch(
            @Parameter(description = "검색어", required = true)
            @RequestParam("q") String query,
            @Parameter(description = "검색 결과 개수", example = "10")
            @RequestParam(name = "topK", defaultValue = "10") Integer topK,
            @Parameter(description = "선택: 사용자 ID 기반 가중치")
            @RequestParam(name = "userId", required = false) Long userId
    ) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("q 파라미터는 필수입니다.");
        }

        List<RecommendService.SemanticSearchResult> results =
                recommendService.semanticSearchWithScores(query, topK, userId != null ? userId.toString() : null);

        SemanticSearchResponse response = new SemanticSearchResponse(
                buildQueryEmbedding(query),
                results.stream()
                        .map(result -> new SemanticSearchResponse.ResultItem(
                                ActivityMapper.toResponse(result.activity()),
                                result.score()
                        ))
                        .collect(Collectors.toList())
        );

        return ResponseEntity.ok(response);
    }

    private List<Double> buildQueryEmbedding(String query) {
        return query.chars()
                .limit(16)
                .mapToDouble(ch -> (double) (ch % 97) / 96.0)
                .boxed()
                .collect(Collectors.toList());
    }
}




