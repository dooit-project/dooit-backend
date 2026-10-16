package pj.dooit.goal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalDraftRequest;
import pj.dooit.goal.dto.GoalDraftResponse;
import pj.dooit.goal.service.GoalDraftService;
import pj.dooit.goal.service.GoalPlanDraftService;
import pj.dooit.goal.dto.GoalPlanDraftRequest;
import pj.dooit.goal.dto.GoalPlanDraftResponse;
import pj.dooit.goal.dto.GoalAdjustmentDraftRequest;
import pj.dooit.goal.dto.GoalAdjustmentDraftResponse;
import pj.dooit.goal.service.GoalAdjustmentDraftService;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Goal Journey", description = "목표 중심 계획·실천 여정 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalDraftV1Controller {
    private final CurrentUserService currentUserService;
    private final GoalDraftService draftService;
    private final GoalPlanDraftService planDraftService;
    private final GoalAdjustmentDraftService adjustmentDraftService;

    @PostMapping("/api/v1/goals/{goalId}/adjustment-draft")
    @Operation(summary = "저장하지 않는 계획 조정안 생성", description = "해당 목표에 연결된 본인 소유 계획의 현재 버전과 사용자 설명으로 텍스트 조정안을 만듭니다. 일정과 상태는 유지하며 자동 저장하지 않습니다. 기본 비활성, 제공자 미설정/실패 시 503/70009입니다.")
    public ResponseEntity<ApiResponse<GoalAdjustmentDraftResponse>> adjustmentDraft(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId, @Valid @RequestBody GoalAdjustmentDraftRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                adjustmentDraftService.draft(goalId, request, currentUserService.requireUser(jwt))));
    }

    @PostMapping("/api/v1/goals/{goalId}/plan-draft")
    @Operation(summary = "저장하지 않는 목표 계획 초안 생성", description = "본인 소유 목표와 사용자가 선택한 일정으로 기본/최소 행동 초안을 만듭니다. 기본 비활성, 제공자 미설정/실패 시 503/70009입니다. 저장은 사용자 확인 후 계획 생성 API로 요청합니다.")
    public ResponseEntity<ApiResponse<GoalPlanDraftResponse>> planDraft(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId, @Valid @RequestBody GoalPlanDraftRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(
                planDraftService.draft(goalId, request, currentUserService.requireUser(jwt))));
    }

    @PostMapping("/api/v1/goals/draft")
    @Operation(summary = "저장하지 않는 목표 초안 생성", description = "기본 비활성 상태이며 제공자 미설정/실패 시 503/70009를 반환합니다. targetDate는 요청값을 유지하고 저장은 사용자 확인 후 별도 목표 생성 API로 요청합니다.")
    public ResponseEntity<ApiResponse<GoalDraftResponse>> draft(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoalDraftRequest request) {
        currentUserService.requireUser(jwt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(draftService.draft(request)));
    }
}
