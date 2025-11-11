package com.mentoai.mentoai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "activity_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTagEntity {

    @EmbeddedId
    private ActivityTagId id = new ActivityTagId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("activityId")
    @JoinColumn(name = "activity_id")
    private ActivityEntity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id")
    private TagEntity tag;
}
