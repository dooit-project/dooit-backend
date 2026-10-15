package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalJourneyResponse;
import pj.dooit.goal.service.GoalJourneyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "v1 Goal Journey", description = "목표 중심 계획·실천 여정 API")
@SecurityRequirement(name = "bearerAuth")
public class GoalJourneyV1Controller {
    private final CurrentUserService currentUserService;
    private final GoalJourneyService journeyService;

    @Operation(summary = "목표 여정 조회", description = "저장된 실천 회차·일회성 Task 완료·직접 기록과 전체 기간 summary를 조회합니다. 회차/Task를 생성하지 않으며 cursor 원본 변경은 409로 반환합니다.")
    @GetMapping("/api/v1/goals/{goalId}/journey")
    public ResponseEntity<ApiResponse<GoalJourneyResponse>> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long goalId, @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String timeZone, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(journeyService.get(goalId, from, to, timeZone,
                cursor, limit, currentUserService.requireUser(jwt))));
    }
}
