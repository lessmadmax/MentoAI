package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityTagId implements Serializable {

    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "tag_id")
    private Long tagId;
}


