package pj.dooit.dailyplan.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.dailyplan.dto.DailyPlanRequest;
import pj.dooit.dailyplan.dto.DailyPlanResponse;
import pj.dooit.dailyplan.dto.DailyPlanSummaryResponse;
import pj.dooit.dailyplan.service.DailyPlanService;
import pj.dooit.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/daily-plans")
@RequiredArgsConstructor
@Tag(name = "v1 Daily Plan", description = "모바일 일일 계획 API")
@SecurityRequirement(name = "bearerAuth")
public class DailyPlanV1Controller {

    private final DailyPlanService dailyPlanService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "일일 계획 조회", description = "로그인 사용자의 지정 날짜 일일 계획을 조회합니다. 저장 전이면 빈 DRAFT 계획을 반환합니다.")
    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<DailyPlanResponse>> getDailyPlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(dailyPlanService.getForOwner(date, owner)));
    }

    @Operation(summary = "일일 계획 결과 요약 조회", description = "계획 확정 시점 focus Task의 현재 결과 count를 조회합니다.")
    @GetMapping("/{date}/summary")
    public ResponseEntity<ApiResponse<DailyPlanSummaryResponse>> getDailyPlanSummary(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(dailyPlanService.getSummaryForOwner(date, owner)));
    }

    @Operation(summary = "일일 계획 저장", description = "로그인 사용자의 지정 날짜 일일 계획을 전체 교체 방식으로 저장합니다.")
    @PutMapping("/{date}")
    public ResponseEntity<ApiResponse<DailyPlanResponse>> replaceDailyPlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable LocalDate date,
            @Valid @RequestBody DailyPlanRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(dailyPlanService.replaceForOwner(date, request, owner)));
    }
}
