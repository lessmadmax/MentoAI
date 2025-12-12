package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.ActivityTagEntity;
import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.repository.ActivityRepository;
import com.mentoai.mentoai.repository.ActivityTagRepository;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityTagService {

    private final ActivityTagRepository activityTagRepository;
    private final TagRepository tagRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public void replaceActivityTags(Long activityId, List<String> tagNames) {
        // 기존 태그 관계를 DB에서 제거해 orphanRemoval 재저장 예외를 방지
        activityTagRepository.deleteByActivityId(activityId);

        ActivityEntity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new IllegalArgumentException("활동을 찾을 수 없습니다: " + activityId));

        if (tagNames == null || tagNames.isEmpty()) {
            activity.setActivityTags(new ArrayList<>());
            activityRepository.save(activity);
            return;
        }

        List<TagEntity> tags = resolveTags(tagNames);
        List<ActivityTagEntity> activityTags = new ArrayList<>();
        for (TagEntity tag : tags) {
            ActivityTagEntity activityTag = new ActivityTagEntity();
            activityTag.setActivity(activity);
            activityTag.setTag(tag);
            activityTags.add(activityTag);
        }
        activity.setActivityTags(activityTags);
        activityRepository.save(activity);
    }

    private List<TagEntity> resolveTags(List<String> tagNames) {
        List<String> distinctNames = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (distinctNames.isEmpty()) {
            return List.of();
        }

        List<TagEntity> tags = new ArrayList<>(tagRepository.findByNameIn(distinctNames));
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
        return tags;
    }
}

