package com.mentoai.mentoai.controller.dto;

import com.mentoai.mentoai.controller.mapper.UserMapper;
import com.mentoai.mentoai.entity.UserEntity;

public record AuthResult(
        AuthTokens tokens,
        UserSummary user,
        String provider
) {

    public static AuthResult of(UserEntity userEntity, AuthTokens tokens) {
        return new AuthResult(
                tokens,
                UserMapper.toSummary(userEntity),
                userEntity.getAuthProvider() != null ? userEntity.getAuthProvider().name() : null
        );
    }
}




