package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.controller.mapper.UserMapper;
import com.mentoai.mentoai.entity.UserEntity;

public record AuthStatus(
        boolean isAuthenticated,
        UserSummary user,
        String provider
) {

    public static AuthStatus anonymous() {
        return new AuthStatus(false, null, null);
    }

    public static AuthStatus authenticated(UserEntity userEntity) {
        return new AuthStatus(
                true,
                UserMapper.toSummary(userEntity),
                userEntity.getAuthProvider() != null ? userEntity.getAuthProvider().name() : null
        );
    }
}


