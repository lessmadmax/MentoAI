package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.TargetRoleResponse;
import com.mentoai.mentoai.controller.dto.TargetRoleUpsertRequest;
import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.entity.WeightedMajor;
import com.mentoai.mentoai.entity.WeightedSkill;
import com.mentoai.mentoai.repository.TargetRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TargetRoleService {

    private final TargetRoleRepository targetRoleRepository;

    public List<TargetRoleResponse> listRoles() {
        return targetRoleRepository.findAll().stream()
                .sorted(Comparator.comparing(TargetRoleEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public TargetRoleResponse getRole(String roleId) {
        return targetRoleRepository.findById(roleId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Target role not found: " + roleId));
    }

    @Transactional
    public TargetRoleResponse upsert(TargetRoleUpsertRequest request) {
        TargetRoleEntity entity = targetRoleRepository.findById(request.roleId())
                .orElseGet(TargetRoleEntity::new);

        entity.setRoleId(request.roleId());
        entity.setName(request.name());
        entity.setExpectedSeniority(request.expectedSeniority());

        entity.getRequiredSkills().clear();
        entity.getRequiredSkills().addAll(fromSkillMap(request.requiredSkills()));

        entity.getBonusSkills().clear();
        entity.getBonusSkills().addAll(fromSkillMap(request.bonusSkills()));

        entity.getMajorMapping().clear();
        entity.getMajorMapping().addAll(fromMajorMap(request.majorMapping()));

        entity.getRecommendedCerts().clear();
        if (request.recommendedCerts() != null) {
            entity.getRecommendedCerts().addAll(request.recommendedCerts());
        }

        TargetRoleEntity saved = targetRoleRepository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public void deleteRole(String roleId) {
        targetRoleRepository.deleteById(roleId);
    }

    private List<WeightedSkill> fromSkillMap(Map<String, Double> map) {
        if (map == null) {
            return List.of();
        }
        return map.entrySet().stream()
                .map(entry -> new WeightedSkill(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<WeightedMajor> fromMajorMap(Map<String, Double> map) {
        if (map == null) {
            return List.of();
        }
        return map.entrySet().stream()
                .map(entry -> new WeightedMajor(entry.getKey(), entry.getValue()))
                .toList();
    }

    private TargetRoleResponse toResponse(TargetRoleEntity entity) {
        return new TargetRoleResponse(
                entity.getRoleId(),
                entity.getName(),
                toMap(entity.getRequiredSkills(), WeightedSkill::getName, WeightedSkill::getWeight),
                toMap(entity.getBonusSkills(), WeightedSkill::getName, WeightedSkill::getWeight),
                toMap(entity.getMajorMapping(), WeightedMajor::getMajor, WeightedMajor::getWeight),
                entity.getExpectedSeniority(),
                entity.getRecommendedCerts(),
                entity.getUpdatedAt()
        );
    }

    private <T> Map<String, Double> toMap(List<T> values,
                                          java.util.function.Function<T, String> keyExtractor,
                                          java.util.function.Function<T, Double> valueExtractor) {
        if (values == null) {
            return Map.of();
        }
        return values.stream()
                .collect(Collectors.toMap(
                        keyExtractor,
                        valueExtractor,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }
}

