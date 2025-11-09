package com.mentoai.mentoai.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 정보")
public class User {
    
    @Schema(description = "사용자 ID", example = "1")
    private Long id;
    
    @Schema(description = "사용자명", example = "홍길동")
    private String name;
    
    @Schema(description = "이메일", example = "hong@example.com")
    private String email;
    
    @Schema(description = "전공", example = "컴퓨터공학과")
    private String major;
    
    @Schema(description = "학년", example = "3")
    private Integer grade;
    
    @Schema(description = "등록일시")
    private LocalDateTime createdAt;
    
    @Schema(description = "사용자 관심사 목록")
    private List<UserInterest> interests;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 관심사")
class UserInterest {
    
    @Schema(description = "관심사 ID", example = "1")
    private Long id;
    
    @Schema(description = "태그 이름", example = "백엔드")
    private String tagName;
    
    @Schema(description = "관심도 점수 (1-5)", example = "4")
    private Integer score;
    
    @Schema(description = "등록일시")
    private LocalDateTime createdAt;
}

