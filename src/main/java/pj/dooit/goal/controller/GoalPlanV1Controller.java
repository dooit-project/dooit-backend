package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanResponse;
import pj.dooit.goal.dto.GoalPlanStatusRequest;
import pj.dooit.goal.dto.GoalPlanUpdateRequest;
import pj.dooit.goal.service.GoalPlanService;
import pj.dooit.user.domain.User;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "v1 Goal Plan", description = "목표 반복 계획 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalPlanV1Controller {

    private final GoalPlanService goalPlanService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "목표 하위 계획 생성", description = "지정한 목표 아래 반복 계획을 생성합니다.")
    @PostMapping("/goals/{goalId}/plans")
    public ResponseEntity<ApiResponse<GoalPlanResponse>> createForGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId,
            @Valid @RequestBody GoalPlanCreateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalPlanService.create(request.withGoalId(goalId), owner)));
    }

    @Operation(summary = "목표 하위 계획 목록", description = "지정한 목표 아래 반복 계획 목록을 조회합니다.")
    @GetMapping("/goals/{goalId}/plans")
    public ResponseEntity<ApiResponse<List<GoalPlanResponse>>> listByGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalPlanService.listByGoal(goalId, owner)));
    }

    @Operation(summary = "계획 생성", description = "goalId가 없으면 목표 없는 독립 반복 계획을 생성합니다.")
    @PostMapping("/goal-plans")
    public ResponseEntity<ApiResponse<GoalPlanResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoalPlanCreateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalPlanService.create(request, owner)));
    }

    @Operation(summary = "계획 목록", description = "goalId 또는 independentOnly로 계획 목록을 필터링합니다.")
    @GetMapping("/goal-plans")
    public ResponseEntity<ApiResponse<List<GoalPlanResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) Boolean independentOnly
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalPlanService.list(goalId, independentOnly, owner)));
    }

    @Operation(summary = "계획 상세", description = "반복 계획 상세를 조회합니다.")
    @GetMapping("/goal-plans/{planId}")
    public ResponseEntity<ApiResponse<GoalPlanResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalPlanService.get(planId, owner)));
    }

    @Operation(summary = "계획 수정", description = "적용일 이후 회차에 새 version을 적용하도록 계획을 수정합니다.")
    @PatchMapping("/goal-plans/{planId}")
    public ResponseEntity<ApiResponse<GoalPlanResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @Valid @RequestBody GoalPlanUpdateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalPlanService.update(planId, request, owner)));
    }

    @Operation(summary = "계획 상태 변경", description = "ACTIVE, PAUSED, ARCHIVED 상태로 변경합니다.")
    @PatchMapping("/goal-plans/{planId}/status")
    public ResponseEntity<ApiResponse<GoalPlanResponse>> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @Valid @RequestBody GoalPlanStatusRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalPlanService.changeStatus(planId, request, owner)));
    }
}
