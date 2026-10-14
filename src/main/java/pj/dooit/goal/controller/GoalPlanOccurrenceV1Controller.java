package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalPlanOccurrenceResponse;
import pj.dooit.goal.service.GoalPlanOccurrenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Goal Plan Occurrence", description = "목표 반복 계획 회차 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalPlanOccurrenceV1Controller {
    private final GoalPlanOccurrenceService occurrenceService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "기간별 계획 회차", description = "1~31일의 선택 요일 회차를 생성·조회합니다. goalId는 당시 목표 연결 기준입니다.")
    @GetMapping("/api/v1/goal-plan-occurrences")
    public ResponseEntity<ApiResponse<List<GoalPlanOccurrenceResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long goalId
    ) {
        return ResponseEntity.ok(ApiResponse.success(occurrenceService.getOccurrences(
                from, to, goalId, currentUserService.requireUser(jwt))));
    }
}
