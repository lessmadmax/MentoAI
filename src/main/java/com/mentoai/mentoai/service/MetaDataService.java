package com.mentoai.mentoai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
import java.util.List;

@Service
public class MetaDataService {

    // ==== [1] 설정 파일(application-local.properties)에서 키 가져오기 ====
    
    // 학교(커리어넷)는 승인 대기 중이라 키 사용 안 함
    // @Value("${api.careernet.key}")
    // private String careerNetKey; 

    @Value("${api.worknet.major.key}")
    private String worknetMajorKey;     // 학과 정보 키

    @Value("${api.worknet.job.key}")
    private String worknetJobKey;       // 직업 정보 키

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

    // ==== [3] 자격증 (CSV 파일 파싱) ====
    private List<String> cachedCertifications = new ArrayList<>();

    @PostConstruct // 서버 시작 시 자동 실행
    public void loadCertificationsCsv() {
        try {
            ClassPathResource resource = new ClassPathResource("certifications.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            
            String header = reader.readLine(); // 헤더 스킵

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                // CSV 구조: [0]코드, [1]구분명, [2]계열명, [3]종목명
                String[] parts = line.split(",");

                if (parts.length >= 4) {
                    String certName = parts[3].trim();
                    certName = certName.replace("\"", ""); 
                    
                    if (!certName.isEmpty()) {
                        cachedCertifications.add(certName);
                    }
                }
            }
            System.out.println("✅ 자격증 데이터 로딩 완료: " + cachedCertifications.size() + "개");

        } catch (Exception e) {
            System.err.println("❌ 자격증 CSV 로딩 실패 (기본값 사용): " + e.getMessage());
            cachedCertifications.add("정보처리기사");
            cachedCertifications.add("SQLD");
            cachedCertifications.add("ADsP");
            cachedCertifications.add("컴퓨터활용능력 1급");
            cachedCertifications.add("컴퓨터활용능력 2급");
            cachedCertifications.add("한국사능력검정시험");
            cachedCertifications.add("TOEIC");
            cachedCertifications.add("OPIC");
        }
    }

    public List<String> getCertifications() {
        return cachedCertifications;
    }

    // ==== [4] 학과 정보 (고용24 API 사용) ====
    public List<String> getMajors() {
        List<String> resultList = new ArrayList<>();
        try {
            String url = "http://openapi.work.go.kr/opi/opi/major/majorCode/majorCodeList.do"
                       + "?authKey=" + worknetMajorKey
                       + "&returnType=JSON"
                       + "&target=MAJORCD"; 

            String responseBody = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode items = root.path("majorCodeList").path("majorList");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    resultList.add(item.path("majorNm").asText());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 학과 API 호출 실패: " + e.getMessage());
            return List.of("컴퓨터공학과", "소프트웨어학과", "경영학과", "전자공학과", "시각디자인학과", "기계공학과"); 
        }
        return resultList;
    }

    // ==== [5] 직업 목록 (고용24 API 사용) ====
    public List<String> getJobs() {
        List<String> resultList = new ArrayList<>();
        try {
            String url = "http://openapi.work.go.kr/opi/opi/jw/jobDictionary.do"
                       + "?authKey=" + worknetJobKey
                       + "&returnType=JSON"
                       + "&target=JOBCD";

            String responseBody = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode items = root.path("jobDictionary").path("jobList");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    resultList.add(item.path("jobName").asText());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 직업 API 호출 실패: " + e.getMessage());
            return List.of("백엔드 개발자", "프론트엔드 개발자", "AI 엔지니어", "데이터 분석가", "풀스택 개발자", "PM(기획자)", "UI/UX 디자이너");
        }
        return resultList;
    }
    
    // ==== [6] 학교 정보 (승인 대기중 -> Mock 반환) ====
    public List<String> getSchools(String query) {
        if (query == null || query.isBlank()) return List.of();
        
        return List.of("경희대학교", "서울대학교", "연세대학교", "고려대학교", "성균관대학교", "한양대학교", "서강대학교").stream()
                .filter(s -> s.contains(query))
                .toList();
    }
}