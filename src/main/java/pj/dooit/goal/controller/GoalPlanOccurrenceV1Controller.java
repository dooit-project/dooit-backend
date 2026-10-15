package pj.dooit.goal.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.goal.dto.GoalPlanOccurrenceResponse;
import pj.dooit.goal.service.GoalPlanOccurrenceService;
import pj.dooit.goal.service.GoalPracticeResultService;
import pj.dooit.goal.dto.GoalPracticeResultRequest;
import pj.dooit.goal.dto.GoalPracticeResultResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final GoalPracticeResultService resultService;
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

    @Operation(summary = "계획 회차 실천 결과 저장", description = "기본/최소/미분류 실천을 저장하고 연결 Task를 완료합니다. 휴식은 먼저 취소해야 합니다.")
    @PutMapping("/api/v1/goal-plan-occurrences/{occurrenceId}/result")
    public ResponseEntity<ApiResponse<GoalPracticeResultResponse>> putResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long occurrenceId,
            @Valid @RequestBody GoalPracticeResultRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(resultService.putResult(
                occurrenceId, request, currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "계획 회차 실천 결과 취소", description = "결과를 삭제하고 회차와 연결 Task를 재개합니다.")
    @DeleteMapping("/api/v1/goal-plan-occurrences/{occurrenceId}/result")
    public ResponseEntity<ApiResponse<Void>> deleteResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        resultService.deleteResult(occurrenceId, occurrenceVersion, currentUserService.requireUser(jwt));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "계획 회차 휴식 선택", description = "회차를 RESTED로 기록하고 연결 Task를 Inbox로 이동합니다. 실천 결과는 먼저 취소해야 합니다.")
    @PutMapping("/api/v1/goal-plan-occurrences/{occurrenceId}/rest")
    public ResponseEntity<ApiResponse<GoalPlanOccurrenceResponse>> rest(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        return ResponseEntity.ok(ApiResponse.success(resultService.rest(
                occurrenceId, occurrenceVersion, currentUserService.requireUser(jwt))));
    }

    @Operation(summary = "계획 회차 휴식 취소", description = "휴식을 취소하고 회차와 연결 Task를 재개합니다.")
    @DeleteMapping("/api/v1/goal-plan-occurrences/{occurrenceId}/rest")
    public ResponseEntity<ApiResponse<Void>> cancelRest(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long occurrenceId,
            @RequestParam(required = false) Long occurrenceVersion
    ) {
        resultService.cancelRest(occurrenceId, occurrenceVersion, currentUserService.requireUser(jwt));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
