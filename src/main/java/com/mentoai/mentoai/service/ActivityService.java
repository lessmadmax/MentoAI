package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ActivityDateUpsertRequest;
import com.mentoai.mentoai.controller.dto.ActivityUpsertRequest;
import com.mentoai.mentoai.controller.dto.AttachmentUpsertRequest;
import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.entity.*;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityStatus;
import com.mentoai.mentoai.entity.ActivityEntity.ActivityType;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private static final int MAX_ROLE_MATCH_FETCH = 200;

    private final ActivityRepository activityRepository;
    private final TagRepository tagRepository;
    private final NotificationService notificationService;
    private final ActivityRoleMatchService activityRoleMatchService;
    private final UserProfileService userProfileService;
    
    public Page<ActivityEntity> getActivities(
            Long userId,
            String query,
            ActivityType type,
            List<String> tagNames,
            Boolean isCampus,
            ActivityStatus status,
            LocalDate deadlineBefore,
            int page,
            int size,
            String sort,
            String direction) {

        Sort.Direction sortDirection;
        try {
            if (direction == null || direction.isBlank()) {
                sortDirection = Sort.Direction.DESC;
            } else {
                sortDirection = Sort.Direction.fromString(direction.toUpperCase());
            }
        } catch (IllegalArgumentException e) {
            sortDirection = Sort.Direction.DESC;
        }
        Sort sortObj = Sort.by(sortDirection, sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // deadlineBefore 필터는 일시적으로 비활성화 (PostgreSQL 타입 추론 문제)
        // TODO: 추후 필요시 별도 쿼리 메서드로 구현

        // userId가 제공되면 사용자 맞춤 추천 적용
        if (userId != null) {
            return getPersonalizedActivities(userId, query, type, tagNames, isCampus, status, pageable);
        }

        // 기존 로직 (일반 조회)
        return activityRepository.search(
                query,
                type,
                (tagNames == null || tagNames.isEmpty()) ? null : tagNames,
                isCampus,
                status,
                null, // deadlineBefore 필터 비활성화
                ActivityDateEntity.DateType.APPLY_END,
                pageable
        );
    }
    
    /**
     * targetRoleId 기반 사용자 맞춤 활동 목록 조회
     */
    private Page<ActivityEntity> getPersonalizedActivities(
            Long userId,
            String query,
            ActivityType type,
            List<String> tagNames,
            Boolean isCampus,
            ActivityStatus status,
            Pageable pageable) {

        UserProfileResponse profile = userProfileService.getProfile(userId);
        String targetRoleId = profile.targetRoleId();
        if (targetRoleId == null || targetRoleId.isBlank()) {
            log.warn("User {} has no targetRoleId configured. Returning empty page.", userId);
            return Page.empty(pageable);
        }

        int fetchSize = determineFetchSize(pageable);
        List<ActivityRoleMatchService.RoleMatch> matches =
                activityRoleMatchService.findRoleMatches(targetRoleId, fetchSize);
        if (matches.isEmpty()) {
            log.warn("No Qdrant matches found for user {} and role {}", userId, targetRoleId);
            return Page.empty(pageable);
        }

        List<Long> ids = matches.stream()
                .map(ActivityRoleMatchService.RoleMatch::activityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, ActivityEntity> activityMap = activityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ActivityEntity::getId, Function.identity()));

        String normalizedQuery = normalizeQuery(query);
        Set<String> requiredTags = normalizeTags(tagNames);

        List<ActivityEntity> ordered = new ArrayList<>();
        for (ActivityRoleMatchService.RoleMatch match : matches) {
            ActivityEntity activity = activityMap.get(match.activityId());
            if (activity == null) {
                continue;
            }
            if (!matchesPersonalizedFilters(activity, normalizedQuery, type, requiredTags, isCampus, status)) {
                continue;
            }
            ordered.add(activity);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), ordered.size());
        List<ActivityEntity> pageContent = start < ordered.size()
                ? ordered.subList(start, end)
                : List.of();

        return new PageImpl<>(pageContent, pageable, ordered.size());
    }
    
    @Transactional
    public ActivityEntity createActivity(ActivityUpsertRequest request) {
        ActivityEntity activity = new ActivityEntity();
        applyUpsert(activity, request);

        ActivityEntity savedActivity = activityRepository.save(activity);
        notificationService.createNewActivityNotification(savedActivity);
        activityRoleMatchService.indexActivity(savedActivity);
        return savedActivity;
    }
    
    public Optional<ActivityEntity> getActivity(Long id) {
        return activityRepository.findById(id);
    }
    
    @Transactional
    public Optional<ActivityEntity> updateActivity(Long id, ActivityUpsertRequest request) {
        return activityRepository.findById(id)
            .map(existingActivity -> {
                applyUpsert(existingActivity, request);
                ActivityEntity updated = activityRepository.save(existingActivity);
                activityRoleMatchService.indexActivity(updated);
                return updated;
            });
    }
    
    @Transactional
    public boolean deleteActivity(Long id) {
        if (activityRepository.existsById(id)) {
            activityRoleMatchService.deleteActivityVector(id);
            activityRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    public List<ActivityEntity> getActiveActivities() {
        return activityRepository.findByStatus(ActivityStatus.OPEN);
    }
    
    public List<ActivityEntity> getCampusActivities(Boolean isCampus) {
        return activityRepository.findByIsCampus(isCampus);
    }

    @Transactional(readOnly = true)
    public List<AttachmentEntity> getAttachments(Long activityId) {
        return activityRepository.findById(activityId)
                .map(activity -> {
                    List<AttachmentEntity> attachments = activity.getAttachments();
                    return attachments == null
                            ? Collections.<AttachmentEntity>emptyList()
                            : List.copyOf(attachments);
                })
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));
    }

    @Transactional
    public AttachmentEntity addAttachment(Long activityId, AttachmentUpsertRequest request) {
        ActivityEntity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));

        if (activity.getAttachments() == null) {
            activity.setAttachments(new ArrayList<>());
        }

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setActivity(activity);
        attachment.setFileType(parseEnum(
                request.fileType(),
                AttachmentEntity.FileType::valueOf,
                AttachmentEntity.FileType.class));
        attachment.setFileUrl(request.fileUrl());
        attachment.setOcrText(request.ocrText());

        activity.getAttachments().add(attachment);
        activityRepository.saveAndFlush(activity);
        return attachment;
    }

    private int determineFetchSize(Pageable pageable) {
        int requested = (pageable.getPageNumber() + 1) * pageable.getPageSize() * 2;
        int minimum = pageable.getPageSize();
        int candidate = Math.max(requested, minimum);
        return Math.min(candidate, MAX_ROLE_MATCH_FETCH);
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().toLowerCase();
    }

    private Set<String> normalizeTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Set.of();
        }
        return tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .collect(Collectors.toSet());
    }

    private boolean matchesPersonalizedFilters(
            ActivityEntity activity,
            String normalizedQuery,
            ActivityType type,
            Set<String> requiredTags,
            Boolean isCampus,
            ActivityStatus status) {

        if (type != null && activity.getType() != type) {
            return false;
        }
        if (status != null && activity.getStatus() != status) {
            return false;
        }
        if (isCampus != null) {
            boolean campus = Boolean.TRUE.equals(activity.getIsCampus());
            if (!isCampus.equals(campus)) {
                return false;
            }
        }
        if (normalizedQuery != null && !matchesQuery(activity, normalizedQuery)) {
            return false;
        }
        if (!requiredTags.isEmpty()) {
            if (activity.getActivityTags() == null || activity.getActivityTags().isEmpty()) {
                return false;
            }
            Set<String> activityTags = activity.getActivityTags().stream()
                    .map(ActivityTagEntity::getTag)
                    .filter(Objects::nonNull)
                    .map(TagEntity::getName)
                    .filter(Objects::nonNull)
                    .map(name -> name.toLowerCase())
                    .collect(Collectors.toSet());
            boolean tagMatched = activityTags.stream().anyMatch(requiredTags::contains);
            if (!tagMatched) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesQuery(ActivityEntity activity, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        String title = activity.getTitle() != null ? activity.getTitle().toLowerCase() : "";
        if (title.contains(normalizedQuery)) {
            return true;
        }
        String summary = activity.getSummary() != null ? activity.getSummary().toLowerCase() : "";
        if (summary.contains(normalizedQuery)) {
            return true;
        }
        String content = activity.getContent() != null ? activity.getContent().toLowerCase() : "";
        return content.contains(normalizedQuery);
    }

    private void applyUpsert(ActivityEntity activity, ActivityUpsertRequest request) {
        activity.setTitle(request.title());
        activity.setSummary(request.summary());
        activity.setContent(request.content());
        activity.setType(parseEnum(request.type(), ActivityType::valueOf, ActivityType.class));
        activity.setOrganizer(request.organizer());
        activity.setLocation(request.location());
        activity.setUrl(request.url());
        activity.setIsCampus(request.isCampus() != null ? request.isCampus() : Boolean.FALSE);
        activity.setStatus(parseEnumOrDefault(request.status(), ActivityStatus::valueOf, ActivityStatus.OPEN));
        activity.setPublishedAt(request.publishedAt());

        syncActivityDates(activity, request.dates());
        syncActivityTags(activity, request.tags());
    }

    private void syncActivityDates(ActivityEntity activity, List<ActivityDateUpsertRequest> dateRequests) {
        if (activity.getDates() == null) {
            activity.setDates(new ArrayList<>());
        } else {
            activity.getDates().clear();
        }
        if (dateRequests == null) {
            return;
        }
        for (ActivityDateUpsertRequest dateRequest : dateRequests) {
            ActivityDateEntity dateEntity = new ActivityDateEntity();
            dateEntity.setActivity(activity);
            dateEntity.setDateType(parseEnum(
                    dateRequest.dateType(),
                    ActivityDateEntity.DateType::valueOf,
                    ActivityDateEntity.DateType.class));
            dateEntity.setDateValue(dateRequest.dateValue());
            activity.getDates().add(dateEntity);
        }
    }

    private void syncActivityTags(ActivityEntity activity, List<String> tagNames) {
        if (activity.getActivityTags() == null) {
            activity.setActivityTags(new ArrayList<>());
        } else {
            activity.getActivityTags().clear();
        }
        if (tagNames == null || tagNames.isEmpty()) {
            return;
        }

        List<String> distinctNames = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (distinctNames.isEmpty()) {
            return;
        }

        List<TagEntity> tags = tagRepository.findByNameIn(distinctNames);
        Set<String> foundNames = tags.stream().map(TagEntity::getName).collect(Collectors.toSet());
        List<String> missing = distinctNames.stream()
                .filter(name -> !foundNames.contains(name))
                .toList();

        if (!missing.isEmpty()) {
            log.info("Create missing tags on the fly: {}", missing);
            List<TagEntity> newTags = missing.stream()
                    .map(name -> {
                        TagEntity tag = new TagEntity();
                        tag.setName(name);
                        tag.setType(TagEntity.TagType.CATEGORY);
                        return tag;
                    })
                    .toList();
            List<TagEntity> saved = tagRepository.saveAll(newTags);
            tags.addAll(saved);
        }

        for (TagEntity tag : tags) {
            ActivityTagEntity activityTag = new ActivityTagEntity();
            activityTag.setActivity(activity);
            activityTag.setTag(tag);
            activity.getActivityTags().add(activityTag);
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, java.util.function.Function<String, E> parser, Class<E> enumClass) {
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            String allowed = String.join(", ", enumNames(enumClass));
            throw new IllegalArgumentException("허용되지 않는 값입니다. value=" + value + ", allowed=" + allowed);
        }
    }

    private <E extends Enum<E>> E parseEnumOrDefault(String value, java.util.function.Function<String, E> parser, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return parseEnum(value, parser, defaultValue.getDeclaringClass());
    }

    private <E extends Enum<E>> List<String> enumNames(Class<E> enumClass) {
        return java.util.Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name)
                .toList();
    }
}
