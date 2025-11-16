package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileSkill {

    @Column(name = "skill_name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private SkillLevel level;
}


