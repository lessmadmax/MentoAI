package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.ImprovementItem;
import com.mentoai.mentoai.controller.dto.RoleFitBatchRequest;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.RoleFitResponse;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationRequest;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationResponse;
import com.mentoai.mentoai.controller.mapper.ActivityMapper;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.repository.UserProfileRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleFitService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RecommendService recommendService;

    public RoleFitResponse calculateRoleFit(Long userId, RoleFitRequest request) {
        UserEntity user = getUser(userId);
        UserProfileEntity profile = userProfileRepository.findById(userId).orElse(null);

        double skillFit = calculateSkillFit(profile);
        double experienceFit = calculateExperienceFit(profile);
        double educationFit = calculateEducationFit(profile);
        double evidenceFit = calculateEvidenceFit(profile);

        double roleFitScore = roundScore((skillFit + experienceFit + educationFit + evidenceFit) / 4 * 100);

        List<RoleFitResponse.MissingSkill> missingSkills = buildMissingSkills(profile, request.target());
        List<String> recommendations = buildRecommendations(request.target());

        return new RoleFitResponse(
                resolveTarget(request.target()),
                roleFitScore,
                new RoleFitResponse.Breakdown(skillFit, experienceFit, educationFit, evidenceFit),
                missingSkills,
                recommendations
        );
    }

    public List<RoleFitResponse> calculateRoleFitBatch(Long userId, RoleFitBatchRequest request) {
        List<String> targets = request.targets() != null && !request.targets().isEmpty()
                ? request.targets()
                : List.of("general");

        return targets.stream()
                .map(target -> calculateRoleFit(userId, new RoleFitRequest(target, request.topNImprovements())))
                .toList();
    }

    public RoleFitSimulationResponse simulateRoleFit(Long userId, RoleFitSimulationRequest request) {
        RoleFitResponse base = calculateRoleFit(userId, new RoleFitRequest(request.target(), null));

        double skillDelta = clamp((request.addSkills() != null ? request.addSkills().size() : 0) * 0.03);
        double experienceDelta = clamp((request.addExperiences() != null ? request.addExperiences().size() : 0) * 0.025);
        double evidenceDelta = clamp((request.addCertifications() != null ? request.addCertifications().size() : 0) * 0.02);
        double educationDelta = 0.0;

        RoleFitResponse.Breakdown deltaBreakdown = new RoleFitResponse.Breakdown(
                skillDelta,
                experienceDelta,
                educationDelta,
                evidenceDelta
        );

        double baseScore = base.roleFitScore();
        double newScore = roundScore(baseScore + (skillDelta + experienceDelta + educationDelta + evidenceDelta) * 25);

        return new RoleFitSimulationResponse(
                baseScore,
                newScore,
                roundScore(newScore - baseScore),
                deltaBreakdown
        );
    }

    public List<ImprovementItem> recommendImprovements(Long userId, String roleId, int size) {
        List<ActivityEntity> activities = fetchImprovementActivities(userId, size);
        String affects = StringUtils.hasText(roleId) && roleId.toLowerCase(Locale.ROOT).contains("backend")
                ? "SKILL"
                : "EXPERIENCE";

        double step = size > 0 ? 5.0 / size : 1.0;
        List<ImprovementItem> items = new ArrayList<>();
        for (int i = 0; i < activities.size(); i++) {
            ActivityEntity activity = activities.get(i);
            double delta = roundScore(Math.max(1.0, 3.0 + (size - i) * step / 2));
            items.add(new ImprovementItem(
                    activity.getType() != null ? activity.getType().name() : "STUDY",
                    ActivityMapper.toResponse(activity),
                    delta,
                    List.of(affects),
                    buildImprovementReason(activity, affects)
            ));
        }
        return items;
    }

    private UserEntity getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private double calculateSkillFit(UserProfileEntity profile) {
        int techCount = profile != null && profile.getTechStack() != null ? profile.getTechStack().size() : 0;
        return clamp(0.4 + techCount * 0.05);
    }

    private double calculateExperienceFit(UserProfileEntity profile) {
        int expCount = profile != null && profile.getExperiences() != null ? profile.getExperiences().size() : 0;
        return clamp(0.3 + expCount * 0.07);
    }

    private double calculateEducationFit(UserProfileEntity profile) {
        if (profile == null) {
            return 0.4;
        }
        boolean hasUniversity = StringUtils.hasText(profile.getUniversityName());
        boolean hasAwards = profile.getAwards() != null && !profile.getAwards().isEmpty();
        return clamp((hasUniversity ? 0.6 : 0.4) + (hasAwards ? 0.15 : 0));
    }

    private double calculateEvidenceFit(UserProfileEntity profile) {
        int creds = profile != null && profile.getCertifications() != null ? profile.getCertifications().size() : 0;
        return clamp(0.25 + creds * 0.08);
    }

    private List<RoleFitResponse.MissingSkill> buildMissingSkills(UserProfileEntity profile, String target) {
        List<String> desiredSkills = switch (resolveTarget(target)) {
            case "backend_entry" -> List.of("Spring", "AWS", "Docker");
            case "data_science" -> List.of("Python", "SQL", "TensorFlow");
            default -> List.of("Communication", "Teamwork");
        };

        List<String> ownedSkills = profile != null && profile.getTechStack() != null
                ? profile.getTechStack().stream()
                .map(skill -> skill.getName() != null ? skill.getName().toLowerCase(Locale.ROOT) : "")
                .toList()
                : Collections.emptyList();

        List<RoleFitResponse.MissingSkill> missing = new ArrayList<>();
        double impact = 0.08;
        for (String skill : desiredSkills) {
            boolean hasSkill = ownedSkills.stream().anyMatch(s -> s.contains(skill.toLowerCase(Locale.ROOT)));
            if (!hasSkill) {
                missing.add(new RoleFitResponse.MissingSkill(skill, impact));
            }
        }
        return missing;
    }

    private List<String> buildRecommendations(String target) {
        if (!StringUtils.hasText(target)) {
            return List.of("Contribute to open-source projects.", "Run a study group in your field.");
        }
        if (target.toLowerCase(Locale.ROOT).contains("backend")) {
            return List.of(
                    "Build a side project with Spring to gain system design experience.",
                    "Prepare for AWS Certified Cloud Practitioner to validate cloud fundamentals."
            );
        }
        if (target.toLowerCase(Locale.ROOT).contains("data")) {
            return List.of(
                    "Join a public data analysis competition to gain hands-on experience.",
                    "Prepare for TensorFlow certification and learn ML pipelines."
            );
        }
        return List.of("Get feedback via mentoring with experts in the field.");
    }

    private List<ActivityEntity> fetchImprovementActivities(Long userId, int size) {
        int safeSize = size > 0 ? size : 5;
        try {
            return recommendService.getRecommendations(userId, safeSize, null, null);
        } catch (Exception ex) {
            return recommendService.getTrendingActivities(safeSize, null);
        }
    }

    private String buildImprovementReason(ActivityEntity activity, String affects) {
        String title = activity.getTitle() != null ? activity.getTitle() : "Activity";
        return "%s contributes to improving %s.".formatted(title, affects);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double roundScore(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String resolveTarget(String target) {
        return StringUtils.hasText(target) ? target : "general";
    }
}


