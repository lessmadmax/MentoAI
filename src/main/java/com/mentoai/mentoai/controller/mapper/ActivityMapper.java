package com.mentoai.mentoai.controller.mapper;

import com.mentoai.mentoai.controller.dto.*;
import com.mentoai.mentoai.entity.ActivityDateEntity;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.AttachmentEntity;
import com.mentoai.mentoai.entity.TagEntity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ActivityMapper {

    private ActivityMapper() {
    }

    public static ActivityResponse toResponse(ActivityEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ActivityResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getContent(),
                entity.getType() != null ? entity.getType().name() : null,
                entity.getOrganizer(),
                entity.getLocation(),
                entity.getUrl(),
                entity.getIsCampus(),
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getVectorDocId(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                mapDates(entity.getDates()),
                mapTags(entity.getActivityTags()),
                mapAttachments(entity.getAttachments())
        );
    }

    private static List<ActivityDateResponse> mapDates(List<ActivityDateEntity> dates) {
        if (dates == null) {
            return Collections.emptyList();
        }
        return dates.stream()
                .filter(Objects::nonNull)
                .map(date -> new ActivityDateResponse(
                        date.getId(),
                        date.getDateType() != null ? date.getDateType().name() : null,
                        date.getDateValue()))
                .collect(Collectors.toList());
    }

    private static List<TagResponse> mapTags(List<ActivityTagEntity> activityTags) {
        if (activityTags == null) {
            return Collections.emptyList();
        }
        return activityTags.stream()
                .map(ActivityTagEntity::getTag)
                .filter(Objects::nonNull)
                .map(ActivityMapper::toTagResponse)
                .collect(Collectors.toList());
    }

    private static TagResponse toTagResponse(TagEntity tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getType() != null ? tag.getType().name() : null
        );
    }

    public static AttachmentResponse toAttachmentResponse(AttachmentEntity attachment) {
        if (attachment == null) {
            return null;
        }
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileType() != null ? attachment.getFileType().name() : null,
                attachment.getFileUrl(),
                attachment.getOcrText(),
                attachment.getCreatedAt()
        );
    }

    private static List<AttachmentResponse> mapAttachments(List<AttachmentEntity> attachments) {
        if (attachments == null) {
            return Collections.emptyList();
        }
        return attachments.stream()
                .filter(Objects::nonNull)
                .map(ActivityMapper::toAttachmentResponse)
                .collect(Collectors.toList());
    }
}


