package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalTaskCreateRequest;
import pj.dooit.goal.dto.GoalTaskLinkRequest;
import pj.dooit.goal.service.GoalTaskLinkService;
import pj.dooit.task.dto.TaskResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Goal Journey", description = "목표 중심 계획·실천 여정 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalTaskV1Controller {
    private final CurrentUserService currentUserService;
    private final GoalTaskLinkService taskLinkService;

    @Operation(summary = "목표 일회성 Task 생성", description = "목표에 연결된 날짜 기반 Today Task를 생성합니다.")
    @PostMapping("/api/v1/goals/{goalId}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId, @Valid @RequestBody GoalTaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                taskLinkService.create(goalId, request, currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "Task 목표 연결", description = "본인 소유 일회성 Task를 목표에 연결합니다. 완료 여부와 D-Day 연결은 유지됩니다.")
    @PutMapping("/api/v1/tasks/{taskId}/goal-link")
    public ResponseEntity<ApiResponse<TaskResponse>> link(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId, @Valid @RequestBody GoalTaskLinkRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                taskLinkService.link(taskId, request.goalId(), currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "Task 목표 연결 해제", description = "Task의 개인 목표 연결만 해제합니다. 반복 회차 연결은 계획 API를 사용합니다.")
    @DeleteMapping("/api/v1/tasks/{taskId}/goal-link")
    public ResponseEntity<ApiResponse<TaskResponse>> unlink(@AuthenticationPrincipal Jwt jwt, @PathVariable Long taskId) {
        return ResponseEntity.ok(ApiResponse.success(taskLinkService.link(taskId, null, currentUserService.requireUser(jwt))));
    }
}
