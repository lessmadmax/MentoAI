package com.mentoai.mentoai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeightedSkill {

    @Column(name = "skill_name")
    private String name;

    @Column(name = "weight")
    private Double weight;
}


