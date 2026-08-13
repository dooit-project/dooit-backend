package com.todolab.workspace.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.dday.dto.DdayGoalRequest;
import com.todolab.dday.dto.DdayGoalResponse;
import com.todolab.dday.service.DdayGoalService;
import com.todolab.user.domain.User;
import com.todolab.workspace.domain.SharedWorkspace;
import com.todolab.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/dday-goals")
@RequiredArgsConstructor
@Tag(name = "v1 Workspace D-Day", description = "공유 workspace D-Day API")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceDdayGoalV1Controller {

    private final WorkspaceService workspaceService;
    private final DdayGoalService ddayGoalService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Workspace D-Day 목표 생성", description = "OWNER 또는 EDITOR가 workspace scope D-Day 목표를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DdayGoalResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @Valid @RequestBody DdayGoalRequest request
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ddayGoalService.createForWorkspace(request, actor, workspace)));
    }

    @Operation(summary = "Workspace D-Day 목표 목록 조회", description = "ACTIVE 멤버가 workspace scope D-Day 목표 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DdayGoalResponse>>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.findAllForWorkspace(workspace)));
    }

    @Operation(summary = "Workspace D-Day 목표 단건 조회", description = "ACTIVE 멤버가 workspace scope D-Day 목표를 단건 조회합니다.")
    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<DdayGoalResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long goalId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.getForWorkspace(goalId, workspace)));
    }
}
