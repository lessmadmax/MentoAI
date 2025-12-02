package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityDateUpsertRequest;
import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.controller.dto.JobPostingUpsertRequest;
import com.mentoai.mentoai.controller.dto.RawActivityPayload;
import com.mentoai.mentoai.controller.dto.RawJobPostingPayload;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.entity.ActivityDateEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 외부 원시 데이터를 현재 시스템 스키마에 맞는 요청 객체로 변환하는 헬퍼.
 */
@Component
public class RawDataSchemaMapper {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String APPLY_END = ActivityDateEntity.DateType.APPLY_END.name();

    public ActivityUpsertRequest toActivityRequest(RawActivityPayload raw) {
        if (raw == null) {
            throw new IllegalArgumentException("활동 원시 데이터가 비어 있습니다.");
        }
        String title = requireText(raw.title(), "활동 제목");
        String type = mapActivityType(raw.category()).name();

        LocalDateTime publishedAt = LocalDateTime.now();
        List<ActivityDateUpsertRequest> dateRequests = buildActivityDates(raw.deadline());
        List<String> tags = buildActivityTags(raw);

        String summary = buildActivitySummary(raw);
        String content = buildActivityContent(raw);

        return new ActivityUpsertRequest(
                title,
                summary,
                content,
                type,
                raw.organization(),
                null,
                raw.url(),
                Boolean.FALSE,
                ActivityStatus.OPEN.name(),
                publishedAt,
                tags,
                dateRequests
        );
    }

    public JobPostingUpsertRequest toJobPostingRequest(RawJobPostingPayload raw) {
        if (raw == null) {
            throw new IllegalArgumentException("채용 원시 데이터가 비어 있습니다.");
        }

        String companyName = requireText(raw.company(), "회사명");
        String title = requireText(raw.title(), "공고 제목");

        WorkPlaceCareer parsed = parseEtc(raw.etc());
        String description = buildJobDescription(raw);

        return new JobPostingUpsertRequest(
                companyName,
                title,
                null, // rank
                null, // jobSector
                null, // employmentType
                parsed.workPlace(),
                parsed.careerLevel(),
                null, // educationLevel
                description,
                null, // requirements
                null, // benefits
                raw.link(),
                null, // deadline
                null, // registeredAt
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    private String buildActivitySummary(RawActivityPayload raw) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(raw.category())) {
            parts.add(raw.category().trim());
        }
        if (StringUtils.hasText(raw.organization())) {
            parts.add("주최: " + raw.organization().trim());
        }
        if (StringUtils.hasText(raw.deadline())) {
            parts.add("마감: " + raw.deadline().trim());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private String buildActivityContent(RawActivityPayload raw) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(raw.url())) {
            lines.add("원문 링크: " + raw.url().trim());
        }
        if (StringUtils.hasText(raw.organization())) {
            lines.add("주최/주관: " + raw.organization().trim());
        }
        if (StringUtils.hasText(raw.category())) {
            lines.add("카테고리: " + raw.category().trim());
        }
        if (StringUtils.hasText(raw.deadline())) {
            lines.add("마감일: " + raw.deadline().trim());
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private ActivityType mapActivityType(String category) {
        if (!StringUtils.hasText(category)) {
            return ActivityType.STUDY;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("공모") || normalized.contains("대회")) {
            return ActivityType.CONTEST;
        }
        if (normalized.contains("캠퍼스") || normalized.contains("교내")) {
            return ActivityType.CAMPUS;
        }
        if (normalized.contains("취업") || normalized.contains("채용")) {
            return ActivityType.JOB;
        }
        return ActivityType.STUDY;
    }

    private List<String> buildActivityTags(RawActivityPayload raw) {
        Set<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(raw.category())) {
            tags.add(raw.category().trim());
        }
        if (StringUtils.hasText(raw.organization())) {
            tags.add(raw.organization().trim());
        }
        return tags.stream().toList();
    }

    private List<ActivityDateUpsertRequest> buildActivityDates(String deadline) {
        LocalDateTime deadlineDateTime = parseDeadline(deadline);
        if (deadlineDateTime == null) {
            return Collections.emptyList();
        }
        return List.of(new ActivityDateUpsertRequest(APPLY_END, deadlineDateTime));
    }

    private LocalDateTime parseDeadline(String rawDeadline) {
        if (!StringUtils.hasText(rawDeadline)) {
            return null;
        }
        String trimmed = rawDeadline.trim();
        try {
            LocalDate date = LocalDate.parse(trimmed, ISO_DATE);
            return date.atTime(23, 59, 0);
        } catch (DateTimeParseException ignored) {
            // 포맷이 다르면 최대한 LocalDateTime으로 바로 파싱 시도
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private WorkPlaceCareer parseEtc(String etc) {
        if (!StringUtils.hasText(etc)) {
            return new WorkPlaceCareer(null, null);
        }
        String[] parts = etc.split("/");
        String workPlace = parts.length > 0 ? parts[0].trim() : null;
        String career = parts.length > 1 ? parts[1].trim() : null;
        return new WorkPlaceCareer(
                StringUtils.hasText(workPlace) ? workPlace : null,
                StringUtils.hasText(career) ? career : null
        );
    }

    private String buildJobDescription(RawJobPostingPayload raw) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(raw.link())) {
            lines.add("원문 링크: " + raw.link().trim());
        }
        if (StringUtils.hasText(raw.imgUrl())) {
            lines.add("대표 이미지: " + raw.imgUrl().trim());
        }
        if (StringUtils.hasText(raw.etc())) {
            lines.add("기타 정보: " + raw.etc().trim());
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + "이(가) 필요합니다.");
        }
        return value.trim();
    }

    private record WorkPlaceCareer(String workPlace, String careerLevel) {
    }
}

