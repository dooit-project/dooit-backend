package com.todolab.workspace.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.task.dto.TaskQueryRequest;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import com.todolab.user.domain.User;
import com.todolab.workspace.domain.SharedWorkspace;
import com.todolab.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/tasks")
@RequiredArgsConstructor
@Tag(name = "v1 Workspace Task", description = "공유 workspace Task API")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceTaskV1Controller {

    private final WorkspaceService workspaceService;
    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Workspace Task 생성", description = "OWNER 또는 EDITOR가 workspace scope Task를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @Valid @RequestBody TaskRequest request
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskService.createForWorkspace(request, actor, workspace)));
    }

    @Operation(summary = "Workspace Task 범위 조회", description = "ACTIVE 멤버가 DAY/WEEK/MONTH 기준 workspace Task를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @Parameter(description = "조회 범위", schema = @Schema(allowableValues = {"DAY", "WEEK", "MONTH"}, example = "DAY"))
            @RequestParam(required = false) String type,
            @Parameter(description = "Task 종류 필터", schema = @Schema(allowableValues = {"TODO", "SCHEDULE", "IDEA"}, example = "SCHEDULE"))
            @RequestParam(required = false) String taskType,
            @Parameter(description = "조회 기준 날짜", schema = @Schema(example = "2026-08-13"))
            @RequestParam(required = false) String date
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        TaskQueryRequest request = TaskQueryRequest.builder()
                .rawType(type)
                .rawTaskType(taskType)
                .rawDate(date)
                .build();
        return ResponseEntity.ok(ApiResponse.success(taskService.getTasksForWorkspace(request, workspace)));
    }

    @Operation(summary = "Workspace Task 단건 조회", description = "ACTIVE 멤버가 workspace scope Task를 단건 조회합니다.")
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        return ResponseEntity.ok(ApiResponse.success(taskService.getTaskForWorkspace(taskId, workspace)));
    }

    @Operation(summary = "Workspace Task 수정", description = "OWNER 또는 EDITOR가 workspace scope Task를 수정합니다.")
    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskRequest request
    ) {
        request.validate();
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        return ResponseEntity.ok(ApiResponse.success(taskService.updateForWorkspace(taskId, request, workspace)));
    }

    @Operation(summary = "Workspace Task 삭제", description = "OWNER 또는 EDITOR가 workspace scope Task를 삭제합니다.")
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        taskService.deleteForWorkspace(taskId, workspace);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Workspace Task D-Day 목표 연결", description = "OWNER 또는 EDITOR가 workspace scope Task를 같은 workspace의 D-Day 목표에 연결합니다.")
    @PatchMapping("/{taskId}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> connectDdayGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId,
            @RequestParam Long ddayGoalId
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        return ResponseEntity.ok(ApiResponse.success(taskService.connectDdayGoalForWorkspace(taskId, ddayGoalId, workspace)));
    }

    @Operation(summary = "Workspace Task D-Day 목표 연결 해제", description = "OWNER 또는 EDITOR가 workspace scope Task의 D-Day 목표 연결을 해제합니다.")
    @DeleteMapping("/{taskId}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> disconnectDdayGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long taskId
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        return ResponseEntity.ok(ApiResponse.success(taskService.disconnectDdayGoalForWorkspace(taskId, workspace)));
    }
}
