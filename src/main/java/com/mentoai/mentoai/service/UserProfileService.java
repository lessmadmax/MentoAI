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

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        return userProfileRepository.findById(userId)
                .map(UserProfileMapper::toResponse)
                .orElse(UserProfileMapper.empty(user));
    }

    @Transactional
    public UserProfileResponse upsertProfile(Long userId, UserProfileUpsertRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        UserProfileEntity profile = userProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserProfileEntity entity = new UserProfileEntity();
                    entity.setUser(user);
                    return entity;
                });

        profile.setUser(user);
        UserProfileMapper.apply(profile, request);

        UserProfileEntity saved = userProfileRepository.save(profile);
        return UserProfileMapper.toResponse(saved);
    }
}


