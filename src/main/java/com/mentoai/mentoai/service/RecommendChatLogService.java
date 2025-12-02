package com.mentoai.mentoai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoai.mentoai.controller.dto.RecommendRequest;
import com.mentoai.mentoai.controller.dto.RecommendResponse;
import com.mentoai.mentoai.entity.ActivityEntity;
import com.mentoai.mentoai.entity.RecommendChatLogEntity;
import com.mentoai.mentoai.repository.RecommendChatLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendChatLogService {

    private final RecommendChatLogRepository recommendChatLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RecommendChatLogEntity createLog(Long userId,
                                            String targetRoleId,
                                            RecommendRequest request,
                                            List<ActivityEntity> candidateActivities,
                                            String ragPrompt) {
        RecommendChatLogEntity log = new RecommendChatLogEntity();
        log.setUserId(userId);
        log.setTargetRoleId(targetRoleId);
        log.setUserQuery(request.query());
        log.setRagPrompt(ragPrompt);
        log.setRequestPayload(writeJson(Map.of(
                "request", request,
                "candidateActivityIds", extractActivityIds(candidateActivities)
        )));
        return recommendChatLogRepository.save(log);
    }

    @Transactional
    public void completeLog(Long logId,
                            String geminiResponse,
                            RecommendResponse response,
                            String modelName) {
        recommendChatLogRepository.findById(logId).ifPresent(log -> {
            log.setGeminiResponse(geminiResponse);
            log.setResponsePayload(writeJson(response));
            log.setModelName(modelName);
        });
    }

    @Transactional(readOnly = true)
    public List<RecommendChatLogEntity> getLogsForUser(Long userId) {
        return recommendChatLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<RecommendChatLogEntity> getLog(Long userId, Long logId) {
        return recommendChatLogRepository.findById(logId)
                .filter(log -> log.getUserId().equals(userId));
    }

    public JsonNode parseJson(String payload) {
        if (payload == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stored JSON payload", e);
            return objectMapper.nullNode();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize payload for recommend chat log", e);
            return null;
        }
    }

    private List<Long> extractActivityIds(List<ActivityEntity> activities) {
        if (activities == null) {
            return Collections.emptyList();
        }
        return activities.stream()
                .map(ActivityEntity::getId)
                .collect(Collectors.toList());
    }
}

