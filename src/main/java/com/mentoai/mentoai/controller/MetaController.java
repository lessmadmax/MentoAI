package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.CertificationDictionary;
import com.mentoai.mentoai.controller.dto.SkillDictionary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/meta")
@Tag(name = "recommend", description = "메타 사전 API")
@RequiredArgsConstructor
public class MetaController {

    @GetMapping("/skills")
    @Operation(summary = "스킬 사전 조회")
    public ResponseEntity<SkillDictionary> getSkillDictionary() {
        SkillDictionary dictionary = new SkillDictionary(
                Map.of(
                        "backend", List.of("서버", "백엔드", "API"),
                        "ml", List.of("머신러닝", "딥러닝", "AI")
                ),
                Map.of(
                        "Java Stack", List.of("Java", "Spring", "JPA"),
                        "Data Stack", List.of("Python", "Pandas", "TensorFlow")
                )
        );
        return ResponseEntity.ok(dictionary);
    }

    @GetMapping("/certifications")
    @Operation(summary = "자격 사전 조회")
    public ResponseEntity<CertificationDictionary> getCertificationDictionary() {
        CertificationDictionary dictionary = new CertificationDictionary(
                List.of(
                        new CertificationDictionary.Item("정보처리기사", List.of("backend_entry", "backend_mid")),
                        new CertificationDictionary.Item("AWS Solutions Architect Associate", List.of("backend_entry", "cloud_engineer")),
                        new CertificationDictionary.Item("SQLD", List.of("data_science", "data_engineer"))
                )
        );
        return ResponseEntity.ok(dictionary);
    }
}

