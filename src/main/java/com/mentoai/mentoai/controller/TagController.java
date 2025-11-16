package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.TagResponse;
import com.mentoai.mentoai.controller.dto.TagUpsertRequest;
import com.mentoai.mentoai.controller.mapper.TagMapper;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.TagEntity.TagType;
import com.mentoai.mentoai.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "tags", description = "태그(직무/스킬/주제/카테고리)")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "태그 목록 조회", description = "태그 목록을 반환합니다.")
    public ResponseEntity<List<TagResponse>> listTags(
            @Parameter(description = "태그 유형 필터") @RequestParam(required = false) TagType type,
            @Parameter(description = "검색어") @RequestParam(required = false) String q) {
        
        List<TagResponse> tags = tagService.getTags(q, type).stream()
                .map(TagMapper::toResponse)
                .toList();
        return ResponseEntity.ok(tags);
    }

    @PostMapping
    @Operation(summary = "태그 생성", description = "새로운 태그를 생성합니다.")
    public ResponseEntity<TagResponse> createTag(@Valid @RequestBody TagUpsertRequest request) {
        TagEntity tag = new TagEntity();
        tag.setName(request.tagName());
        try {
            tag.setType(TagType.valueOf(request.tagType().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }

        TagEntity created = tagService.createTag(tag);
        return ResponseEntity.status(201).body(TagMapper.toResponse(created));
    }
}
