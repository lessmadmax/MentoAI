package com.mentoai.mentoai.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AiApiKeyGuard {

    private final String configuredKey;

    public AiApiKeyGuard(@Value("${application.ai.api-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public void verify(String providedKey) {
        if (!StringUtils.hasText(configuredKey)) {
            return;
        }
        if (!StringUtils.hasText(providedKey) || !configuredKey.equals(providedKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid AI API key");
        }
    }
}


