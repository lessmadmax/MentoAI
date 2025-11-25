package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.JobPostingUpsertRequest;
import com.mentoai.mentoai.entity.JobPostingEntity;
import com.mentoai.mentoai.entity.JobPostingRoleEntity;
import com.mentoai.mentoai.entity.JobPostingRoleId;
import com.mentoai.mentoai.entity.JobPostingSkillEntity;
import com.mentoai.mentoai.entity.JobPostingSkillId;
import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.repository.JobPostingRepository;
import com.mentoai.mentoai.repository.TargetRoleRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final TargetRoleRepository targetRoleRepository;

    @Transactional
    public JobPostingEntity createJobPosting(JobPostingUpsertRequest request) {
        JobPostingEntity entity = new JobPostingEntity();
        applyBasicFields(entity, request);

        JobPostingEntity saved = jobPostingRepository.save(entity);
        replaceSkills(saved, request.skills());
        replaceTargetRoles(saved, request.targetRoles());
        return saved;
    }

    @Transactional
    public List<JobPostingEntity> createJobPostings(List<JobPostingUpsertRequest> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }

        List<JobPostingEntity> created = new ArrayList<>();
        for (JobPostingUpsertRequest request : requests) {
            created.add(createJobPosting(request));
        }
        return created;
    }

    @Transactional
    public JobPostingEntity updateJobPosting(Long jobId, JobPostingUpsertRequest request) {
        JobPostingEntity entity = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("채용 공고를 찾을 수 없습니다: " + jobId));

        applyBasicFields(entity, request);
        replaceSkills(entity, request.skills());
        replaceTargetRoles(entity, request.targetRoles());
        return entity;
    }

    @Transactional(readOnly = true)
    public Optional<JobPostingEntity> getJobPosting(Long jobId) {
        return jobPostingRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public Page<JobPostingEntity> searchJobPostings(String keyword,
                                                    String companyName,
                                                    String jobSector,
                                                    String employmentType,
                                                    String targetRoleId,
                                                    OffsetDateTime deadlineAfter,
                                                    OffsetDateTime deadlineBefore,
                                                    Pageable pageable) {
        Specification<JobPostingEntity> specification = Specification.where(null);

        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("requirements")), like),
                    cb.like(cb.lower(root.get("companyName")), like)
            ));
        }

        if (StringUtils.hasText(companyName)) {
            String like = "%" + companyName.toLowerCase() + "%";
            specification = specification.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("companyName")), like)
            );
        }

        if (StringUtils.hasText(jobSector)) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("jobSector"), jobSector)
            );
        }

        if (StringUtils.hasText(employmentType)) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("employmentType"), employmentType)
            );
        }

        if (deadlineAfter != null) {
            specification = specification.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("deadline"), deadlineAfter)
            );
        }

        if (deadlineBefore != null) {
            specification = specification.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("deadline"), deadlineBefore)
            );
        }

        if (StringUtils.hasText(targetRoleId)) {
            specification = specification.and((root, query, cb) -> {
                query.distinct(true);
                Join<JobPostingEntity, JobPostingRoleEntity> join = root.join("targetRoles", JoinType.LEFT);
                return cb.equal(join.get("id").get("targetRoleId"), targetRoleId);
            });
        }

        return jobPostingRepository.findAll(specification, pageable);
    }

    private void applyBasicFields(JobPostingEntity entity, JobPostingUpsertRequest request) {
        entity.setCompanyName(request.companyName());
        entity.setTitle(request.title());
        entity.setRank(request.rank());
        entity.setJobSector(request.jobSector());
        entity.setEmploymentType(request.employmentType());
        entity.setWorkPlace(request.workPlace());
        entity.setCareerLevel(request.careerLevel());
        entity.setEducationLevel(request.educationLevel());
        entity.setDescription(request.description());
        entity.setRequirements(request.requirements());
        entity.setBenefits(request.benefits());
        entity.setLink(request.link());
        entity.setDeadline(request.deadline());
        entity.setRegisteredAt(request.registeredAt());
    }

    private void replaceSkills(JobPostingEntity entity, List<JobPostingUpsertRequest.JobPostingSkillPayload> skills) {
        if (entity.getSkills() == null) {
            entity.setSkills(new ArrayList<>());
        } else {
            entity.getSkills().clear();
        }

        if (CollectionUtils.isEmpty(skills)) {
            return;
        }

        for (JobPostingUpsertRequest.JobPostingSkillPayload payload : skills) {
            JobPostingSkillEntity skillEntity = new JobPostingSkillEntity();
            skillEntity.setJobPosting(entity);
            skillEntity.setId(new JobPostingSkillId(entity.getId(), payload.skillName()));
            skillEntity.setSkillName(payload.skillName());
            skillEntity.setProficiency(payload.proficiency());
            entity.getSkills().add(skillEntity);
        }
    }

    private void replaceTargetRoles(JobPostingEntity entity, List<JobPostingUpsertRequest.JobPostingRolePayload> roles) {
        if (entity.getTargetRoles() == null) {
            entity.setTargetRoles(new ArrayList<>());
        } else {
            entity.getTargetRoles().clear();
        }

        if (CollectionUtils.isEmpty(roles)) {
            return;
        }

        Set<String> roleIds = roles.stream()
                .map(JobPostingUpsertRequest.JobPostingRolePayload::targetRoleId)
                .collect(Collectors.toSet());

        Map<String, TargetRoleEntity> targetRoleMap = targetRoleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(TargetRoleEntity::getRoleId, Function.identity()));

        for (String roleId : roleIds) {
            if (!targetRoleMap.containsKey(roleId)) {
                throw new IllegalArgumentException("존재하지 않는 타겟 직무 ID입니다: " + roleId);
            }
        }

        for (JobPostingUpsertRequest.JobPostingRolePayload payload : roles) {
            TargetRoleEntity targetRole = targetRoleMap.get(payload.targetRoleId());

            JobPostingRoleEntity roleEntity = new JobPostingRoleEntity();
            roleEntity.setJobPosting(entity);
            roleEntity.setTargetRole(targetRole);
            roleEntity.setId(new JobPostingRoleId(entity.getId(), payload.targetRoleId()));
            roleEntity.setRelevance(payload.relevance());
            entity.getTargetRoles().add(roleEntity);
        }
    }
}


