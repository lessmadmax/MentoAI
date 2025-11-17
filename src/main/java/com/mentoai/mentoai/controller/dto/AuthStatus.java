package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.controller.mapper.UserMapper;
import com.mentoai.mentoai.entity.UserEntity;

public record AuthStatus(
        boolean isAuthenticated,
        UserSummary user,
        String provider,
        boolean profileComplete  // 추가
) {

    public static AuthStatus anonymous() {
        return new AuthStatus(false, null, null, false);
    }

    public static AuthStatus authenticated(UserEntity userEntity, boolean profileComplete) {
        return new AuthStatus(
                true,
                UserMapper.toSummary(userEntity),
                userEntity.getAuthProvider() != null ? userEntity.getAuthProvider().name() : null,
                profileComplete
        );
    }
}
