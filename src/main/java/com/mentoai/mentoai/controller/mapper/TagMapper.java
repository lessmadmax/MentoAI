package com.mentoai.mentoai.controller.mapper;

import com.mentoai.mentoai.controller.dto.TagResponse;
import com.mentoai.mentoai.entity.TagEntity;

public final class TagMapper {

    private TagMapper() {
    }

    public static TagResponse toResponse(TagEntity tag) {
        if (tag == null) {
            return null;
        }
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getType() != null ? tag.getType().name() : null
        );
    }
}



