package com.mentoai.mentoai.controller;

import com.mentoai.mentoai.controller.dto.TargetRoleResponse;
import com.mentoai.mentoai.controller.dto.TargetRoleUpsertRequest;
import com.mentoai.mentoai.service.TargetRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@Tag(name = "recommend", description = "타깃 직무 정의 API")
@RequiredArgsConstructor
public class TargetRoleController {

    private final TargetRoleService targetRoleService;

    @GetMapping
    @Operation(summary = "직무 목록 조회")
    public ResponseEntity<List<TargetRoleResponse>> listRoles() {
        return ResponseEntity.ok(targetRoleService.listRoles());
    }

    @GetMapping("/{roleId}")
    @Operation(summary = "직무 상세 조회")
    public ResponseEntity<TargetRoleResponse> getRole(@PathVariable String roleId) {
        try {
            return ResponseEntity.ok(targetRoleService.getRole(roleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @Operation(summary = "직무 정의 업서트")
    public ResponseEntity<TargetRoleResponse> upsertRole(
            @Valid @RequestBody TargetRoleUpsertRequest request
    ) {
        TargetRoleResponse response = targetRoleService.upsert(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{roleId}")
    @Operation(summary = "직무 정의 삭제")
    public ResponseEntity<Void> deleteRole(@PathVariable String roleId) {
        targetRoleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}




