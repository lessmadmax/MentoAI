package com.mentoai.mentoai.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "target_roles")
@Getter
@Setter
@NoArgsConstructor
public class TargetRoleEntity {

    @Id
    @Column(name = "role_id")
    private String roleId;

    private String name;

    @Column(name = "expected_seniority")
    private String expectedSeniority;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "target_role_required_skills", joinColumns = @JoinColumn(name = "role_id"))
    private List<WeightedSkill> requiredSkills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "target_role_bonus_skills", joinColumns = @JoinColumn(name = "role_id"))
    private List<WeightedSkill> bonusSkills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "target_role_major_mapping", joinColumns = @JoinColumn(name = "role_id"))
    private List<WeightedMajor> majorMapping = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "target_role_recommended_certs", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "name")
    private List<String> recommendedCerts = new ArrayList<>();
}




