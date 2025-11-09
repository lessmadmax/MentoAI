package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.TagEntity.TagType;
import com.mentoai.mentoai.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "tags", description = "태그(직무/스킬/주제/카테고리)")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "태그 목록 조회", description = "태그 목록을 반환합니다.")
    public ResponseEntity<Page<TagEntity>> listTags(
            @Parameter(description = "태그 유형 필터") @RequestParam(required = false) TagType type,
            @Parameter(description = "검색어") @RequestParam(required = false) String q,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "50") Integer size) {
        
        Page<TagEntity> tags = tagService.getTags(q, type, page, size);
        return ResponseEntity.ok(tags);
    }

    @PostMapping
    @Operation(summary = "태그 생성", description = "새로운 태그를 생성합니다.")
    public ResponseEntity<TagEntity> createTag(@RequestBody TagEntity tag) {
        try {
            TagEntity createdTag = tagService.createTag(tag);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdTag);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
