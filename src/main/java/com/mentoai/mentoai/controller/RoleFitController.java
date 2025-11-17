package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.ImprovementItem;
import com.mentoai.mentoai.controller.dto.RoleFitBatchRequest;
import com.mentoai.mentoai.controller.dto.RoleFitRequest;
import com.mentoai.mentoai.controller.dto.RoleFitResponse;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationRequest;
import com.mentoai.mentoai.controller.dto.RoleFitSimulationResponse;
import com.mentoai.mentoai.service.RoleFitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}")
@Tag(name = "recommend", description = "직무 적합도/개선 API")
@RequiredArgsConstructor
public class RoleFitController {

    private final RoleFitService roleFitService;

    @PostMapping("/role-fit")
    @Operation(summary = "직무 적합도 평가", description = "특정 직무에 대한 적합도 점수를 계산합니다.")
    public ResponseEntity<RoleFitResponse> calculateRoleFit(
            @PathVariable Long userId,
            @Valid @RequestBody RoleFitRequest request
    ) {
        return ResponseEntity.ok(roleFitService.calculateRoleFit(userId, request));
    }

    @PostMapping("/role-fit/batch")
    @Operation(summary = "직무 적합도 일괄 평가", description = "여러 직무에 대한 적합도를 한 번에 계산합니다.")
    public ResponseEntity<List<RoleFitResponse>> calculateRoleFitBatch(
            @PathVariable Long userId,
            @Valid @RequestBody RoleFitBatchRequest request
    ) {
        return ResponseEntity.ok(roleFitService.calculateRoleFitBatch(userId, request));
    }

    @PostMapping("/role-fit/simulate")
    @Operation(summary = "직무 적합도 시뮬레이션", description = "가상의 학습/경험을 추가했을 때 점수 변화를 예측합니다.")
    public ResponseEntity<RoleFitSimulationResponse> simulateRoleFit(
            @PathVariable Long userId,
            @Valid @RequestBody RoleFitSimulationRequest request
    ) {
        return ResponseEntity.ok(roleFitService.simulateRoleFit(userId, request));
    }

    @GetMapping("/improvements")
    @Operation(summary = "개선 제안", description = "목표 직무에 필요한 개선 활동을 추천합니다.")
    public ResponseEntity<List<ImprovementItem>> recommendImprovements(
            @PathVariable Long userId,
            @Parameter(description = "직무/roleId", required = true) @RequestParam String roleId,
            @RequestParam(defaultValue = "5") Integer size
    ) {
        return ResponseEntity.ok(roleFitService.recommendImprovements(userId, roleId, size != null ? size : 5));
    }
}




