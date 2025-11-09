package com.mentoai.mentoai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "태그 정보")
public class Tag {
    
    @Schema(description = "태그 ID", example = "1")
    private Long id;
    
    @Schema(description = "태그 이름", example = "백엔드")
    private String name;
    
    @Schema(description = "태그 유형", example = "SKILL", allowableValues = {"SKILL", "JOB", "TOPIC", "CATEGORY"})
    private String type;
    
    @Schema(description = "태그 설명", example = "백엔드 개발 관련 태그")
    private String description;
    
    @Schema(description = "등록일시")
    private LocalDateTime createdAt;
}

