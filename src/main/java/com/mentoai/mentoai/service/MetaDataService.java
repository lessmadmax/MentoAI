package com.mentoai.mentoai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class MetaDataService {

    // ==== [1] 설정 파일(application-local.properties)에서 키 가져오기 ====
    
    @Value("${api.careernet.key}")
    private String careerNetKey; 

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==== [2] 기술 스택 (최대한 많이 포함) ====
    public List<String> getTechStacks() {
        List<String> allStacks = new ArrayList<>(Arrays.asList(
            // Languages
            "Java", "Python", "JavaScript", "TypeScript", "C", "C++", "C#", "Go", "Rust", "Kotlin", "Swift",
            "PHP", "Ruby", "Dart", "Scala", "R", "MATLAB", "SQL", "HTML/CSS", "Shell Script", "Assembly",

            // Frontend & Mobile
            "React", "Vue.js", "Angular", "Svelte", "Next.js", "Nuxt.js", "jQuery", "Bootstrap", "Tailwind CSS",
            "Redux", "Recoil", "Zustand", "React Native", "Flutter", "Expo", "Android", "iOS", "Unity", "Unreal Engine",
            "Three.js", "WebGL",

            // Backend & Frameworks
            "Spring Boot", "Spring Framework", "Node.js", "Express.js", "NestJS", "Django", "Flask", "FastAPI",
            "ASP.NET", "Laravel", "Ruby on Rails", "GraphQL", "gRPC", "Socket.io", "Kafka", "RabbitMQ", "Nginx", "Apache",

            // Data & AI
            "Pandas", "NumPy", "Scikit-learn", "TensorFlow", "PyTorch", "Keras", "OpenCV", "Hadoop", "Spark",
            "Airflow", "Elasticsearch", "Logstash", "Kibana", "Tableau", "PowerBI", "Jupyter", "NLP", "LLM", "RAG",

            // DevOps & Cloud & Infra
            "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Jenkins", "GitHub Actions", "GitLab CI", "Terraform", "Ansible",
            "Linux", "Unix", "Git", "Vagrant", "Prometheus", "Grafana",

            // Database
            "MySQL", "PostgreSQL", "Oracle", "MariaDB", "MongoDB", "Redis", "SQLite", "MSSQL", "DynamoDB", "Firebase", "Supabase",

            // Tools & Collaboration
            "Figma", "Jira", "Slack", "Notion", "Postman", "Swagger", "Zeplin", "Adobe XD"
        ));
        
        // 가나다/ABC 순 정렬 (선택 사항)
        Collections.sort(allStacks, String.CASE_INSENSITIVE_ORDER);
        
        return allStacks;
    }

    // ==== [3] 자격증/학과/직업 (CSV 파일 파싱) ====
    private final List<String> cachedCertifications = new ArrayList<>();
    private final List<String> cachedMajors = new ArrayList<>();
    private final List<String> cachedJobs = new ArrayList<>();

    @PostConstruct
    public void loadStaticMetadata() {
        loadCertificationsCsv();
        loadMajorsCsv();
        loadJobsCsv();
    }

    private void loadCertificationsCsv() {
        cachedCertifications.clear();
        Set<String> merged = new LinkedHashSet<>();

        merged.addAll(readOfficialCertifications("certifications.csv"));
        merged.addAll(readPrivateCertifications("private_certifications.csv"));

        if (merged.isEmpty()) {
            System.err.println("❌ 자격증 CSV 로딩 실패 (기본값 사용)");
            cachedCertifications.addAll(Arrays.asList(
                "정보처리기사",
                "SQLD",
                "ADsP",
                "컴퓨터활용능력 1급",
                "컴퓨터활용능력 2급",
                "한국사능력검정시험",
                "TOEIC",
                "OPIC"
            ));
        } else {
            cachedCertifications.addAll(merged);
            Collections.sort(cachedCertifications, String.CASE_INSENSITIVE_ORDER);
            System.out.println("✅ 자격증 데이터 로딩 완료: " + cachedCertifications.size() + "개");
        }
    }

    private List<String> readOfficialCertifications(String fileName) {
        List<String> items = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            reader.readLine(); // 헤더 스킵

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> parts = splitCsvLine(line);
                if (parts.size() >= 4) {
                    String certName = sanitize(parts.get(3));
                    if (!certName.isEmpty()) {
                        items.add(certName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 공식 자격증 CSV 로딩 실패: " + e.getMessage());
        }
        return items;
    }

    private List<String> readPrivateCertifications(String fileName) {
        List<String> items = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            reader.readLine(); // 헤더 스킵
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> parts = splitCsvLine(line);
                if (parts.size() < 6) continue;

                String name = sanitize(parts.get(4));
                if (name.isEmpty()) continue;

                String gradeColumn = sanitize(parts.get(5));

                if (gradeColumn.isBlank() || gradeColumn.contains("등급없음")) {
                    items.add(name);
                    continue;
                }

                String normalized = gradeColumn.replace("|", ",");
                String[] gradeTokens = normalized.split(",");
                for (String rawGrade : gradeTokens) {
                    String grade = rawGrade.trim();
                    if (grade.isEmpty() || grade.equalsIgnoreCase("등급없음")) continue;
                    items.add(String.format("%s(%s)", name, grade));
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ 민간 자격증 CSV 로딩 실패: " + e.getMessage());
        }
        return items;
    }

    private String sanitize(String raw) {
        return raw == null ? "" : raw.replace("\"", "").trim();
    }

    private List<String> splitCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                tokens.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        tokens.add(current.toString());
        return tokens;
    }

    public List<String> getCertifications() {
        return cachedCertifications;
    }

    // ==== [4] 학과 정보 (고용24 API 사용) ====
    public List<String> getMajors() {
        if (cachedMajors.isEmpty()) {
            loadMajorsCsv();
        }
        return List.copyOf(cachedMajors);
    }

    // ==== [5] 직업 목록 (고용24 API 사용) ====
    public List<String> getJobs() {
        if (cachedJobs.isEmpty()) {
            loadJobsCsv();
        }
        return List.copyOf(cachedJobs);
    }
    
    // ==== [6] 학교 정보 (승인 대기중 -> Mock 반환) ====
    public List<String> getSchools(String query) {
        if (query == null || query.isBlank()) return List.of();
        
        List<String> resultList = new ArrayList<>();
        try {
            String url = UriComponentsBuilder
                .fromUriString("https://www.career.go.kr/cnet/openapi/getOpenApi")
                .queryParam("apiKey", careerNetKey)
                .queryParam("svcType", "api")
                .queryParam("svcCode", "SCHOOL")
                .queryParam("contentType", "json")
                .queryParam("gubun", "univ_list")
                .queryParam("searchSchulNm", query)
                .build()
                .encode()
                .toUriString();

            String responseBody = restTemplate.getForObject(url, String.class);

            // [JSON 파싱] ObjectMapper 사용
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("dataSearch").path("content");

            if (contentNode.isArray()) {
                for (JsonNode item : contentNode) {
                    // 학교명 추출
                    resultList.add(item.path("schoolName").asText());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 학교 API 호출 실패: " + e.getMessage());
            // 실패 시 빈 리스트 반환 (사용자는 검색 결과 없음으로 인지)
            return List.of(); 
        }
        return resultList;
    }

    private void loadMajorsCsv() {
        cachedMajors.clear();
        loadCsvColumn("majors.csv", 1, cachedMajors, "학과");
    }

    private void loadJobsCsv() {
        cachedJobs.clear();
        loadCsvColumn("jobs.csv", 3, cachedJobs, "직업");
    }

    private void loadCsvColumn(String fileName, int columnIndex, List<String> target, String label) {
        try {
            ClassPathResource resource = new ClassPathResource(fileName);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            reader.readLine(); // 헤더 스킵
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length <= columnIndex) continue;
                String value = parts[columnIndex].replace("\"", "").trim();
                if (!value.isEmpty()) {
                    target.add(value);
                }
            }
            System.out.println("✅ " + label + " CSV 로딩 완료: " + target.size() + "개");
        } catch (Exception e) {
            System.err.println("❌ " + label + " CSV 로딩 실패: " + e.getMessage());
        }
    }
}

