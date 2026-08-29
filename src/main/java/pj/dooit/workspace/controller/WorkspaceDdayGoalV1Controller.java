package pj.dooit.workspace.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.dday.dto.DdayGoalRequest;
import pj.dooit.dday.dto.DdayGoalResponse;
import pj.dooit.dday.service.DdayGoalService;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.SharedWorkspace;
import pj.dooit.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    @ResponseStatus(HttpStatus.CREATED)
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

    @Operation(summary = "Workspace D-Day 연결 Task 조회", description = "ACTIVE 멤버가 workspace scope D-Day 목표에 연결된 Task 목록을 조회합니다.")
    @GetMapping("/{goalId}/tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> findTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long goalId
    ) {
        User member = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireReadableWorkspace(workspaceId, member);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.findTasksForWorkspace(goalId, workspace)));
    }

    @Operation(summary = "Workspace D-Day 목표 삭제", description = "OWNER 또는 EDITOR가 workspace scope D-Day 목표를 삭제하고 연결된 workspace Task의 D-Day 연결을 해제합니다.")
    @DeleteMapping("/{goalId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long workspaceId,
            @PathVariable Long goalId
    ) {
        User actor = currentUserService.requireUser(jwt);
        SharedWorkspace workspace = workspaceService.requireEditableWorkspace(workspaceId, actor);
        ddayGoalService.deleteForWorkspace(goalId, workspace);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
