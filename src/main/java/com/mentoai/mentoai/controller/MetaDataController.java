package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.service.MetaDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meta")
@RequiredArgsConstructor
public class MetaDataController {

    private final MetaDataService metaDataService;

    // 1. 기술 스택 (수동 리스트)
    @GetMapping("/skills")
    public ResponseEntity<List<String>> getSkills() {
        return ResponseEntity.ok(metaDataService.getTechStacks());
    }

    // 2. 자격증 (CSV + 기본값)
    @GetMapping("/certifications")
    public ResponseEntity<List<String>> getCertifications() {
        return ResponseEntity.ok(metaDataService.getCertifications());
    }

    // 3. 학교 (Mock 데이터)
    @GetMapping("/schools")
    public ResponseEntity<List<String>> getSchools(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(metaDataService.getSchools(q));
    }

    // 4. 학과 (고용24 API)
    @GetMapping("/majors")
    public ResponseEntity<List<String>> getMajors() {
        return ResponseEntity.ok(metaDataService.getMajors());
    }

    // 5. 직업 (고용24 API)
    @GetMapping("/jobs")
    public ResponseEntity<List<String>> getJobs() {
        return ResponseEntity.ok(metaDataService.getJobs());
    }
}