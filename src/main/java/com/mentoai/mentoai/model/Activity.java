package com.mentoai.mentoai.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "활동 정보")
public class Activity {
    
    @Schema(description = "활동 ID", example = "1")
    private Long id;
    
    @Schema(description = "활동 제목", example = "2024년 하반기 개발자 채용공고")
    private String title;
    
    @Schema(description = "활동 내용/설명", example = "우리 회사에서 개발자를 모집합니다...")
    private String content;
    
    @Schema(description = "활동 유형", example = "JOB", allowableValues = {"JOB", "CONTEST", "STUDY", "CAMPUS"})
    private String type;
    
    @Schema(description = "주최/회사명", example = "네이버")
    private String organizer;
    
    @Schema(description = "활동 URL", example = "https://recruit.navercorp.com")
    private String url;
    
    @Schema(description = "교내 활동 여부", example = "false")
    private Boolean isCampus;
    
    @Schema(description = "활동 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "CLOSED", "UPCOMING"})
    private String status;
    
    @Schema(description = "시작일시")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;
    
    @Schema(description = "마감일시")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endDate;
    
    @Schema(description = "등록일시")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Schema(description = "수정일시")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Schema(description = "연관 태그 목록")
    private List<String> tags;
    
    @Schema(description = "첨부파일 목록")
    private List<Attachment> attachments;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "첨부파일 정보")
class Attachment {
    
    @Schema(description = "첨부파일 ID", example = "1")
    private Long id;
    
    @Schema(description = "파일명", example = "recruitment.pdf")
    private String filename;
    
    @Schema(description = "파일 URL", example = "https://example.com/files/recruitment.pdf")
    private String url;
    
    @Schema(description = "파일 크기 (bytes)", example = "1024000")
    private Long size;
    
    @Schema(description = "MIME 타입", example = "application/pdf")
    private String mimeType;
}

