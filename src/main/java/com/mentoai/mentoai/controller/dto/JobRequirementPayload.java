package com.mentoai.mentoai.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobRequirementPayload(
        List<String> requiredSkills,
        List<String> preferredSkills,
        List<String> requiredExperiences,
        List<String> preferredExperiences,
        List<String> requiredMajors,
        List<String> preferredMajors,
        List<String> requiredCertifications,
        List<String> preferredCertifications,
        String expectedSeniority
) {

    public JobRequirementPayload {
        requiredSkills = normalize(requiredSkills);
        preferredSkills = normalize(preferredSkills);
        requiredExperiences = normalize(requiredExperiences);
        preferredExperiences = normalize(preferredExperiences);
        requiredMajors = normalize(requiredMajors);
        preferredMajors = normalize(preferredMajors);
        requiredCertifications = normalize(requiredCertifications);
        preferredCertifications = normalize(preferredCertifications);
        expectedSeniority = expectedSeniority != null ? expectedSeniority.trim() : null;
    }

    public static JobRequirementPayload empty() {
        return new JobRequirementPayload(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
    }

    public boolean isEmpty() {
        return requiredSkills.isEmpty() &&
                preferredSkills.isEmpty() &&
                requiredExperiences.isEmpty() &&
                preferredExperiences.isEmpty() &&
                requiredMajors.isEmpty() &&
                preferredMajors.isEmpty() &&
                requiredCertifications.isEmpty() &&
                preferredCertifications.isEmpty() &&
                !StringUtils.hasText(expectedSeniority);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }
}

