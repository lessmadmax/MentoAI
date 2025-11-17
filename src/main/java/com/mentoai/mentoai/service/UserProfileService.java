package com.mentoai.mentoai.service;

import com.mentoai.mentoai.controller.dto.UserProfileResponse;
import com.mentoai.mentoai.controller.dto.UserProfileUpsertRequest;
import com.mentoai.mentoai.controller.mapper.UserProfileMapper;
import com.mentoai.mentoai.entity.UserEntity;
import com.mentoai.mentoai.entity.UserProfileEntity;
import com.mentoai.mentoai.repository.UserProfileRepository;
import com.mentoai.mentoai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return userProfileRepository.findById(userId)
                .map(UserProfileMapper::toResponse)
                .orElse(UserProfileMapper.empty(user));
    }

    @Transactional
    public UserProfileResponse upsertProfile(Long userId, UserProfileUpsertRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 기존 프로필 존재 여부 확인
        boolean exists = userProfileRepository.existsById(userId);
        
        UserProfileEntity profile;
        
        if (!exists) {
            // 새로 생성된 경우: 먼저 빈 엔티티를 저장
            profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setUser(user);
            profile.setInterestDomains(new ArrayList<>());
            profile.setTechStack(new ArrayList<>());
            profile.setAwards(new ArrayList<>());
            profile.setCertifications(new ArrayList<>());
            profile.setExperiences(new ArrayList<>());
            
            // 먼저 저장하여 관리 상태로 만듦
            profile = userProfileRepository.saveAndFlush(profile);
            
            // 저장 후 다시 로드하여 완전한 관리 상태 보장
            profile = userProfileRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Failed to save new profile"));
        } else {
            // 기존 프로필 로드
            profile = userProfileRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Profile not found: " + userId));
        }

        // 이제 안전하게 수정 가능
        UserProfileMapper.apply(profile, request);
        
        UserProfileEntity saved = userProfileRepository.saveAndFlush(profile);
        
        return UserProfileMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public boolean isProfileComplete(Long userId) {
        return userProfileRepository.findById(userId)
                .map(profile -> {
                    // 필수 필드 체크: 대학교 정보가 있는지 확인
                    return profile.getUniversityName() != null && 
                           !profile.getUniversityName().isBlank() &&
                           profile.getUniversityMajor() != null && 
                           !profile.getUniversityMajor().isBlank() &&
                           profile.getUniversityGrade() != null;
                })
                .orElse(false);
    }
}
