package com.mentoai.mentoai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags", uniqueConstraints = @UniqueConstraint(name = "uq_tag", columnNames = {"tag_name", "tag_type"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(name = "tag_name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", nullable = false)
    private TagType type;

    public enum TagType {
        ROLE, SKILL, TOPIC, CATEGORY
    }
}
