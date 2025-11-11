package com.mentoai.mentoai.service;

import com.mentoai.mentoai.entity.TagEntity;
import com.mentoai.mentoai.entity.TagEntity.TagType;
import com.mentoai.mentoai.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {
    
    private final TagRepository tagRepository;
    
    public List<TagEntity> getTags(String query, TagType type) {
        return tagRepository.findByFilters(query, type, Pageable.unpaged()).getContent();
    }
    
    @Transactional
    public TagEntity createTag(TagEntity tag) {
        if (tagRepository.existsByNameAndType(tag.getName(), tag.getType())) {
            throw new IllegalArgumentException("이미 존재하는 태그입니다: " + tag.getName() + " (" + tag.getType() + ")");
        }
        return tagRepository.save(tag);
    }
    
    public Optional<TagEntity> getTagByName(String name) {
        return tagRepository.findByName(name);
    }
    
    public List<TagEntity> getTagsByType(TagType type) {
        return tagRepository.findByType(type);
    }
    
    public List<TagEntity> getAllTags() {
        return tagRepository.findAll();
    }
}



