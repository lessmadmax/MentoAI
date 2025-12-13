package com.mentoai.mentoai.config;

import com.mentoai.mentoai.entity.TargetRoleEntity;
import com.mentoai.mentoai.repository.TargetRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One-off seeder to import job codes from {@code src/main/resources/jobs.csv}
 * into {@code target_roles}. Disabled by default; enable with
 * {@code target-role.seed-from-csv=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetRoleCsvSeeder implements ApplicationRunner {

    private final TargetRoleRepository targetRoleRepository;
    private final ResourceLoader resourceLoader;

    @Value("${target-role.seed-from-csv:false}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!seedEnabled) {
            return;
        }

        Resource resource = resourceLoader.getResource("classpath:jobs.csv");
        if (!resource.exists()) {
            log.warn("[TargetRoleCsvSeeder] jobs.csv not found; skipping seed.");
            return;
        }

        log.info("[TargetRoleCsvSeeder] Seeding target_roles from jobs.csv ...");
        List<TargetRoleEntity> toSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // skip header
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // CSV columns: "jobClcd","jobClcdNM","jobCd","jobNm"[,"keywords"]
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length < 4) {
                    continue;
                }
                String jobClcd = stripQuotes(parts[0]);
                String jobClcdNm = stripQuotes(parts[1]);
                String jobCd = stripQuotes(parts[2]);
                String jobNm = stripQuotes(parts[3]);
                String keywordsRaw = parts.length >= 5 ? stripQuotes(parts[4]) : null;
                if (jobCd == null || jobCd.isBlank()) {
                    continue;
                }

                TargetRoleEntity entity = targetRoleRepository.findById(jobCd)
                        .orElseGet(TargetRoleEntity::new);
                entity.setRoleId(jobCd);
                entity.setName(jobNm);
                // Fill a heuristic expectedSeniority only when missing
                if (entity.getExpectedSeniority() == null || entity.getExpectedSeniority().isBlank()) {
                    entity.setExpectedSeniority(inferSeniority(jobClcdNm, jobNm));
                }
                // Populate dummy weights for skills/majors/certs by cluster
                DummyProfile dummy = inferDummyProfile(jobClcd, jobClcdNm, jobNm);
                entity.getRequiredSkills().clear();
                entity.getRequiredSkills().addAll(dummy.requiredSkills());
                entity.getBonusSkills().clear();
                entity.getBonusSkills().addAll(dummy.bonusSkills());
                entity.getMajorMapping().clear();
                entity.getMajorMapping().addAll(dummy.majorMapping());
                entity.getRecommendedCerts().clear();
                entity.getRecommendedCerts().addAll(dummy.recommendedCerts());
                // Keywords: prefer CSV-provided, fallback to heuristic so every role has keywords
                entity.getKeywords().clear();
                List<String> csvKeywords = parseKeywords(keywordsRaw);
                if (!csvKeywords.isEmpty()) {
                    // 키워드 중 접두어로 스킬/자격증을 구분하여 주입
                    for (String kw : csvKeywords) {
                        if (kw == null || kw.isBlank()) continue;
                        String trimmed = kw.trim();
                        String lower = trimmed.toLowerCase();
                        if (lower.startsWith("skill:")) {
                            String skillName = trimmed.substring(6).trim();
                            if (!skillName.isEmpty()) {
                                entity.getRequiredSkills().add(new com.mentoai.mentoai.entity.WeightedSkill(skillName, 1.0));
                            }
                            continue;
                        }
                        if (lower.startsWith("bonus:")) {
                            String skillName = trimmed.substring(6).trim();
                            if (!skillName.isEmpty()) {
                                entity.getBonusSkills().add(new com.mentoai.mentoai.entity.WeightedSkill(skillName, 0.6));
                            }
                            continue;
                        }
                        if (lower.startsWith("cert:")) {
                            String certName = trimmed.substring(5).trim();
                            if (!certName.isEmpty()) {
                                entity.getRecommendedCerts().add(certName);
                            }
                            continue;
                        }
                        entity.getKeywords().add(trimmed);
                    }
                } else {
                    entity.getKeywords().addAll(inferKeywords(jobClcd, jobClcdNm, jobNm));
                }

                // 필수/우대/자격이 비어 있으면 기본값 한두 개를 채워 넣어 최소 한 건 이상 보유하도록 보강
                if (entity.getRequiredSkills().isEmpty()) {
                    entity.getRequiredSkills().add(new com.mentoai.mentoai.entity.WeightedSkill("general-skill", 0.8));
                }
                if (entity.getBonusSkills().isEmpty()) {
                    entity.getBonusSkills().add(new com.mentoai.mentoai.entity.WeightedSkill("communication", 0.5));
                }
                if (entity.getRecommendedCerts().isEmpty()) {
                    entity.getRecommendedCerts().add("기본자격");
                }

                if (entity.getCreatedAt() == null) {
                    entity.setCreatedAt(OffsetDateTime.now());
                }
                entity.setUpdatedAt(OffsetDateTime.now());
                toSave.add(entity);
            }
        }

        if (toSave.isEmpty()) {
            log.warn("[TargetRoleCsvSeeder] No rows to seed.");
            return;
        }

        targetRoleRepository.saveAll(toSave);
        log.info("[TargetRoleCsvSeeder] Seeded {} target_roles (upsert).", toSave.size());
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String inferSeniority(String jobClcdNm, String jobNm) {
        String haystack = ((jobClcdNm != null ? jobClcdNm : "") + " " + (jobNm != null ? jobNm : "")).toLowerCase();
        if (haystack.contains("고위") || haystack.contains("관리자") || haystack.contains("임원")) {
            return "senior";
        }
        if (haystack.contains("전문가") || haystack.contains("연구원") || haystack.contains("개발자")) {
            return "mid";
        }
        if (haystack.contains("사무원") || haystack.contains("보조") || haystack.contains("강사") || haystack.contains("교사")) {
            return "junior";
        }
        return "junior";
    }

    private DummyProfile inferDummyProfile(String jobClcd, String jobClcdNm, String jobNm) {
        String text = (jobClcd + " " + jobClcdNm + " " + jobNm).toLowerCase();

        // Software / Data / Security
        if (text.contains("소프트웨어") || text.contains("데이터") || text.contains("it") || text.contains("정보보안") || text.contains("컴퓨터") || text.contains("네트워크")) {
            return DummyProfile.software();
        }
        // Management / Marketing / Business
        if (text.contains("경영") || text.contains("마케팅") || text.contains("광고") || text.contains("홍보") || text.contains("기획") || text.contains("영업") || text.contains("행정")) {
            return DummyProfile.business();
        }
        // Design / Arts
        if (text.contains("디자인") || text.contains("예술") || text.contains("방송")) {
            return DummyProfile.design();
        }
        // Engineering / Construction / Manufacturing
        if (text.contains("건설") || text.contains("토목") || text.contains("기계") || text.contains("전기") || text.contains("전자") || text.contains("화학") || text.contains("환경") || text.contains("제조") || text.contains("생산")) {
            return DummyProfile.engineering();
        }
        // Education / Research
        if (text.contains("연구") || text.contains("교사") || text.contains("교수") || text.contains("강사") || text.contains("교육")) {
            return DummyProfile.education();
        }
        // Finance
        if (text.contains("금융") || text.contains("보험") || text.contains("회계") || text.contains("세무") || text.contains("투자")) {
            return DummyProfile.finance();
        }

        return DummyProfile.general();
    }

    private List<String> inferKeywords(String jobClcd, String jobClcdNm, String jobNm) {
        String text = (jobClcd + " " + jobClcdNm + " " + jobNm).toLowerCase();
        if (text.contains("소프트웨어") || text.contains("it") || text.contains("개발") || text.contains("데이터") || text.contains("정보보안") || text.contains("컴퓨터") || text.contains("네트워크")) {
            return List.of("개발", "소프트웨어", "웹", "앱", "프로그래밍", "코딩", "백엔드", "프론트엔드", "풀스택", "API", "데이터", "AI", "머신러닝", "보안", "클라우드");
        }
        if (text.contains("마케팅") || text.contains("광고") || text.contains("홍보") || text.contains("기획") || text.contains("영업") || text.contains("경영")) {
            return List.of("마케팅", "광고", "브랜딩", "디지털", "SNS", "CRM", "세일즈", "기획", "시장조사", "데이터분석");
        }
        if (text.contains("디자인") || text.contains("예술") || text.contains("방송")) {
            return List.of("디자인", "UI", "UX", "그래픽", "영상", "브랜딩", "포토샵", "일러스트레이터", "모션");
        }
        if (text.contains("건설") || text.contains("토목") || text.contains("기계") || text.contains("전기") || text.contains("전자") || text.contains("화학") || text.contains("환경") || text.contains("제조") || text.contains("생산")) {
            return List.of("엔지니어링", "설계", "제조", "생산", "품질", "안전", "CAD", "프로세스");
        }
        if (text.contains("연구") || text.contains("교사") || text.contains("교수") || text.contains("강사") || text.contains("교육")) {
            return List.of("연구", "교육", "논문", "분석", "실험", "자료조사");
        }
        if (text.contains("금융") || text.contains("보험") || text.contains("회계") || text.contains("세무") || text.contains("투자")) {
            return List.of("금융", "회계", "세무", "투자", "리스크", "재무분석", "엑셀", "보고서");
        }
        return List.of("전문가", "직무", "역량", "기술", "경험");
    }

    private List<String> parseKeywords(String keywordsRaw) {
        if (keywordsRaw == null || keywordsRaw.isBlank()) {
            return List.of();
        }
        String[] tokens = keywordsRaw.split("[;,]");
        List<String> list = new ArrayList<>();
        for (String token : tokens) {
            String t = token.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        // dedup while preserving order
        return list.stream().distinct().toList();
    }

    private record DummyProfile(
            List<com.mentoai.mentoai.entity.WeightedSkill> requiredSkills,
            List<com.mentoai.mentoai.entity.WeightedSkill> bonusSkills,
            List<com.mentoai.mentoai.entity.WeightedMajor> majorMapping,
            List<String> recommendedCerts
    ) {
        static DummyProfile software() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("programming", 1.0),
                            new com.mentoai.mentoai.entity.WeightedSkill("sql", 0.8)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("cloud", 0.5),
                            new com.mentoai.mentoai.entity.WeightedSkill("docker", 0.4)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("컴퓨터공학", 1.0),
                            new com.mentoai.mentoai.entity.WeightedMajor("소프트웨어", 0.8)
                    ),
                    List.of("정보처리기사")
            );
        }

        static DummyProfile business() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("excel", 0.7),
                            new com.mentoai.mentoai.entity.WeightedSkill("presentation", 0.6)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("english", 0.4)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("경영학", 1.0),
                            new com.mentoai.mentoai.entity.WeightedMajor("경제학", 0.8)
                    ),
                    List.of("워드프로세서")
            );
        }

        static DummyProfile design() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("design", 0.7)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("photoshop", 0.6),
                            new com.mentoai.mentoai.entity.WeightedSkill("illustrator", 0.6)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("디자인", 1.0)
                    ),
                    List.of()
            );
        }

        static DummyProfile engineering() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("safety", 0.6)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("cad", 0.5)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("기계공학", 1.0),
                            new com.mentoai.mentoai.entity.WeightedMajor("토목공학", 1.0)
                    ),
                    List.of()
            );
        }

        static DummyProfile education() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("research", 0.6)
                    ),
                    List.of(),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("교육학", 0.8)
                    ),
                    List.of()
            );
        }

        static DummyProfile finance() {
            return new DummyProfile(
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("finance", 0.7),
                            new com.mentoai.mentoai.entity.WeightedSkill("excel", 0.6)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedSkill("english", 0.4)
                    ),
                    List.of(
                            new com.mentoai.mentoai.entity.WeightedMajor("경영학", 1.0),
                            new com.mentoai.mentoai.entity.WeightedMajor("경제학", 1.0)
                    ),
                    List.of("펀드투자권유대행인")
            );
        }

        static DummyProfile general() {
            return new DummyProfile(List.of(), List.of(), List.of(), List.of());
        }
    }
}


