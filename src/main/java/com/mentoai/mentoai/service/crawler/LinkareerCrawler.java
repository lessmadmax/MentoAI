package com.mentoai.mentoai.service.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkareerCrawler implements ExternalActivityCrawler {

    private final ObjectMapper objectMapper;

    @Value("${external.crawler.linkareer.path:../Mentoai-DE}")
    private String crawlerPath;

    @Value("${external.crawler.python.path:python3}")
    private String pythonPath;

    @Override
    public String getSourceName() {
        return "linkareer";
    }

    @Override
    public List<ExternalActivity> crawlAll() {
        return executeCrawlerScript("total_linkareer.py");
    }

    @Override
    public List<ExternalActivity> crawlRecent() {
        return executeCrawlerScript("partial_linkareer.py");
    }

    /**
     * Python 크롤러 스크립트 실행
     */
    private List<ExternalActivity> executeCrawlerScript(String scriptName) {
        List<ExternalActivity> activities = new ArrayList<>();
        
        try {
            Path scriptPath = Paths.get(crawlerPath, scriptName);
            log.info("Executing Linkareer crawler script: {}", scriptPath);
            
            // Python 스크립트 실행
            ProcessBuilder processBuilder = new ProcessBuilder(
                    pythonPath,
                    scriptPath.toString()
            );
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    log.debug("Crawler output: {}", line);
                }
            }
            
            // 프로세스 종료 대기
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.error("Linkareer crawler script failed with exit code: {}", exitCode);
                log.error("Output: {}", output.toString());
                return activities;
            }
            
            // JSON 파싱
            String jsonOutput = output.toString().trim();
            if (jsonOutput.isEmpty()) {
                log.warn("Linkareer crawler script returned empty output");
                return activities;
            }
            
            // JSON 배열 파싱
            List<Map<String, Object>> rawActivities = objectMapper.readValue(
                    jsonOutput,
                    new TypeReference<List<Map<String, Object>>>() {}
            );
            
            // ExternalActivity로 변환
            for (Map<String, Object> raw : rawActivities) {
                try {
                    ExternalActivity activity = parseExternalActivity(raw);
                    if (activity != null) {
                        activities.add(activity);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Linkareer activity: {}", raw, e);
                }
            }
            
            log.info("Successfully crawled {} activities from Linkareer ({})", activities.size(), scriptName);
            
        } catch (Exception e) {
            log.error("Error executing Linkareer crawler script: {}", scriptName, e);
        }
        
        return activities;
    }

    /**
     * 원시 데이터를 ExternalActivity로 파싱
     */
    private ExternalActivity parseExternalActivity(Map<String, Object> raw) {
        try {
            String title = getStringValue(raw, "title");
            String id = getStringValue(raw, "id");
            Long recruitCloseAt = getLongValue(raw, "recruitCloseAt");
            String organizationName = getStringValue(raw, "organizationName");
            String field = getStringValue(raw, "field");
            
            if (title == null || title.trim().isEmpty()) {
                return null;
            }
            
            // URL 생성
            String url = id != null 
                    ? "https://linkareer.com/activity/" + id 
                    : null;
            
            return new ExternalActivity(
                    getSourceName(),
                    title,
                    id,
                    recruitCloseAt,
                    organizationName,
                    field,
                    url
            );
        } catch (Exception e) {
            log.warn("Failed to parse ExternalActivity from raw data: {}", raw, e);
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        return value.toString();
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}


