package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.config.CorsConfig;
import pj.dooit.goal.dto.GoalDirectLogRequest;
import pj.dooit.goal.dto.GoalDirectLogResponse;
import pj.dooit.goal.service.GoalDirectLogService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Goal Journey", description = "목표 중심 계획·실천 여정 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalDirectLogV1Controller {
    private final CurrentUserService currentUserService;
    private final GoalDirectLogService directLogService;

    @Operation(summary = "목표 직접 실천 기록 생성", description = "Idempotency-Key가 필수입니다. 같은 key와 정규화한 본문은 기존 기록의 현재 상태를 반환하고 다른 본문은 409로 거절합니다.")
    @PostMapping("/api/v1/goals/{goalId}/direct-logs")
    public ResponseEntity<ApiResponse<GoalDirectLogResponse>> create(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId, @RequestHeader(CorsConfig.IDEMPOTENCY_KEY_HEADER) String key,
            @Valid @RequestBody GoalDirectLogRequest request) {
        var result = directLogService.create(goalId, key, request, currentUserService.requireUser(jwt));
        var response = ResponseEntity.status(HttpStatus.CREATED);
        if (result.replayed()) {
            response.header(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true");
        }
        return response.body(ApiResponse.success(result.response()));
    }

    @Operation(summary = "목표 직접 실천 기록 수정", description = "날짜·timezone·제목·메모 전체를 교체합니다. 원래 목표 snapshot과 생성 idempotency key는 보존됩니다.")
    @PatchMapping("/api/v1/goal-direct-logs/{logId}")
    public ResponseEntity<ApiResponse<GoalDirectLogResponse>> update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long logId, @Valid @RequestBody GoalDirectLogRequest request) {
        return ResponseEntity.ok(ApiResponse.success(directLogService.update(logId, request, currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "목표 직접 실천 기록 취소", description = "기록을 삭제하지 않고 canceledAt을 남깁니다. 재시도는 같은 취소 상태를 유지합니다.")
    @DeleteMapping("/api/v1/goal-direct-logs/{logId}")
    public ResponseEntity<ApiResponse<Void>> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long logId) {
        directLogService.cancel(logId, currentUserService.requireUser(jwt));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
