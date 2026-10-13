package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalFuturePlanPolicyRequest;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.dto.GoalStatusRequest;
import pj.dooit.goal.dto.GoalUpdateRequest;
import pj.dooit.goal.service.GoalService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
@Tag(name = "v1 Goal Journey", description = "목표 중심 계획·실천 여정 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalV1Controller {

    private final GoalService goalService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "목표 생성", description = "목표일이 없을 수 있는 로그인 사용자의 목표를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<GoalResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoalCreateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(goalService.create(request, owner)));
    }

    @Operation(summary = "목표 목록 조회", description = "로그인 사용자의 목표 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<GoalResponse>>> list(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.list(owner)));
    }

    @Operation(summary = "목표 상세 조회", description = "로그인 사용자의 목표 상세를 조회합니다.")
    @GetMapping("/{goalId}")
    public ResponseEntity<ApiResponse<GoalResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.get(goalId, owner)));
    }

    @Operation(summary = "목표 수정", description = "목표 제목, 원하는 결과, 이유, 목표일, D-Day 연결을 수정하고 새 version을 남깁니다.")
    @PatchMapping("/{goalId}")
    public ResponseEntity<ApiResponse<GoalResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId,
            @Valid @RequestBody GoalUpdateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.update(goalId, request, owner)));
    }

    @Operation(summary = "목표 상태 변경", description = "ACTIVE, PAUSED, ACHIEVED, ARCHIVED 상태로 변경합니다.")
    @PatchMapping("/{goalId}/status")
    public ResponseEntity<ApiResponse<GoalResponse>> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId,
            @Valid @RequestBody GoalStatusRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.changeStatus(goalId, request, owner)));
    }

    @Operation(summary = "목표 달성 확인", description = "사용자 확인에 의해 목표를 달성 상태로 기록합니다.")
    @PostMapping("/{goalId}/achievement")
    public ResponseEntity<ApiResponse<GoalResponse>> achieve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId,
            @RequestBody(required = false) GoalFuturePlanPolicyRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.achieve(goalId, request, owner)));
    }

    @Operation(summary = "목표 달성 취소", description = "달성 확인을 취소하고 목표를 ACTIVE로 되돌립니다.")
    @DeleteMapping("/{goalId}/achievement")
    public ResponseEntity<ApiResponse<GoalResponse>> cancelAchievement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(goalService.cancelAchievement(goalId, owner)));
    }
}
