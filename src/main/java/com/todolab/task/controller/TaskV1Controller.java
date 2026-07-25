package com.todolab.task.controller;

import com.todolab.Constant;
import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.task.domain.DeferReason;
import com.todolab.task.domain.RecurrenceEditScope;
import com.todolab.task.domain.TodayOrderDirection;
import com.todolab.task.dto.TaskQueryRequest;
import com.todolab.task.dto.TaskRecommendationResponse;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.dto.TaskSearchRequest;
import com.todolab.task.dto.TaskSearchResponse;
import com.todolab.task.dto.TodayOrderRequest;
import com.todolab.task.service.TaskService;
import com.todolab.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "v1 Task", description = "모바일 Task API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청값 검증 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Task 또는 연결 리소스 없음",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        )
})
public class TaskV1Controller {

    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Task 생성", description = "로그인 사용자의 Task를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskRequest request
    ) {
        request.validate();
        User owner = currentUserService.requireUser(jwt);
        TaskResponse response = taskService.createForOwner(request, owner);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "Task 단건 조회", description = "로그인 사용자의 Task를 단건 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getTaskForOwner(id, owner)));
    }

    @Operation(summary = "Task 범위 조회", description = "DAY/WEEK/MONTH 기준으로 로그인 사용자의 Task를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(
                    description = "조회 범위. DAY/WEEK는 date=YYYY-MM-DD, MONTH는 date=YYYY-MM 형식을 사용합니다.",
                    schema = @Schema(allowableValues = {"DAY", "WEEK", "MONTH"}, example = "MONTH")
            )
            @RequestParam(required = false) String type,
            @Parameter(
                    description = "Task 종류 필터",
                    schema = @Schema(allowableValues = {"TODO", "SCHEDULE", "IDEA"}, example = "SCHEDULE")
            )
            @RequestParam(required = false) String taskType,
            @Parameter(
                    description = "조회 기준 날짜. DAY/WEEK는 YYYY-MM-DD, MONTH는 YYYY-MM 형식입니다.",
                    schema = @Schema(example = "2026-07")
            )
            @RequestParam(required = false) String date
    ) {
        User owner = currentUserService.requireUser(jwt);
        TaskQueryRequest request = TaskQueryRequest.builder()
                .rawType(type)
                .rawTaskType(taskType)
                .rawDate(date)
                .build();

        return ResponseEntity.ok(ApiResponse.success(taskService.getTasksForOwner(request, owner)));
    }

    @Operation(
            summary = "Task 통합 검색",
            description = "로그인 사용자의 Task를 텍스트, 상태, 종류, 카테고리, D-Day 연결 여부, 날짜 기준으로 검색합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<TaskSearchResponse>> searchTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "제목 또는 설명 부분 검색어", schema = @Schema(example = "출시"))
            @RequestParam(required = false) String q,
            @Parameter(description = "상태 필터. 반복 또는 콤마 구분을 지원합니다.", schema = @Schema(allowableValues = {"INBOX", "TODAY", "DONE"}))
            @RequestParam(required = false) List<String> statuses,
            @Parameter(description = "Task 종류 필터. 반복 또는 콤마 구분을 지원합니다.", schema = @Schema(allowableValues = {"TODO", "SCHEDULE", "IDEA"}))
            @RequestParam(required = false) List<String> taskTypes,
            @Parameter(description = "카테고리명 exact match", schema = @Schema(example = "업무"))
            @RequestParam(required = false) String category,
            @Parameter(description = "연결된 D-Day 목표 ID", schema = @Schema(example = "1"))
            @RequestParam(required = false) Long ddayGoalId,
            @Parameter(description = "D-Day 목표 연결 여부", schema = @Schema(example = "true"))
            @RequestParam(required = false) Boolean hasDday,
            @Parameter(description = "종일 일정 여부", schema = @Schema(example = "false"))
            @RequestParam(required = false) Boolean allDay,
            @Parameter(
                    description = "날짜 필터/정렬 기준",
                    schema = @Schema(allowableValues = {"PLANNED", "START", "TARGET", "COMPLETED", "CREATED", "UPDATED"}, example = "PLANNED")
            )
            @RequestParam(required = false) String dateField,
            @Parameter(description = "날짜 범위 시작일", schema = @Schema(type = "string", format = "date", example = "2026-07-01"))
            @RequestParam(required = false) LocalDate dateFrom,
            @Parameter(description = "날짜 범위 종료일", schema = @Schema(type = "string", format = "date", example = "2026-07-31"))
            @RequestParam(required = false) LocalDate dateTo,
            @Parameter(
                    description = "정렬 기준",
                    schema = @Schema(allowableValues = {
                            "RELEVANT_DATE_ASC", "RELEVANT_DATE_DESC",
                            "CREATED_AT_ASC", "CREATED_AT_DESC",
                            "UPDATED_AT_ASC", "UPDATED_AT_DESC"
                    }, example = "RELEVANT_DATE_ASC")
            )
            @RequestParam(required = false) String sort,
            @Parameter(description = "이전 응답의 nextCursor. 마지막 항목의 Task id입니다.", schema = @Schema(example = "42"))
            @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기. 1 이상 100 이하", schema = @Schema(example = "50", minimum = "1", maximum = "100"))
            @RequestParam(required = false) Integer limit
    ) {
        User owner = currentUserService.requireUser(jwt);
        TaskSearchRequest request = new TaskSearchRequest(
                q,
                statuses,
                taskTypes,
                category,
                ddayGoalId,
                hasDday,
                allDay,
                dateField,
                dateFrom,
                dateTo,
                sort,
                cursor,
                limit
        );

        return ResponseEntity.ok(ApiResponse.success(taskService.searchTasksForOwner(request, owner)));
    }

    @Operation(summary = "Inbox Task 조회", description = "로그인 사용자의 Inbox Task 목록을 조회합니다.")
    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getInboxTasks(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getInboxTasksForOwner(owner)));
    }

    @Operation(summary = "Today 추천 Task 조회", description = "요청 날짜 기준 로그인 사용자에게 추천할 Today Task 목록을 조회합니다.")
    @GetMapping({"/recommendations/today", "/today/recommendations"})
    public ResponseEntity<ApiResponse<List<TaskRecommendationResponse>>> getTodayRecommendations(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "추천 기준 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getTodayRecommendationsForOwner(date, owner)));
    }

    @Operation(summary = "Today Task 조회", description = "요청 날짜의 Today Task와 겹치는 일정을 조회합니다.")
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTodayTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Today 조회 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getTodayTasksForOwner(date, owner)));
    }

    @Operation(summary = "지난 미완료 Task 조회", description = "요청 날짜 이전의 미완료 Task를 조회합니다.")
    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getOverdueTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "지난 미완료 기준 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getOverdueTasksForOwner(date, owner)));
    }

    @Operation(summary = "오래된 미완료 Task 조회", description = "기준 날짜 이전의 미완료 Task를 조회합니다.")
    @GetMapping("/stale")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getStaleTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "오래된 미완료 기준 날짜. 생략하면 서버 현재 날짜를 사용합니다.", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam(required = false) LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        LocalDate referenceDate = date == null ? LocalDate.now(Constant.ZONE) : date;
        return ResponseEntity.ok(ApiResponse.success(taskService.getOverdueTasksForOwner(referenceDate, owner)));
    }

    @Operation(summary = "완료 Task 조회", description = "요청 날짜에 완료된 Task를 조회합니다.")
    @GetMapping("/done")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getDoneTasks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "완료 조회 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getDoneTasksForOwner(date, owner)));
    }

    @Operation(summary = "Task 수정", description = "로그인 사용자의 Task를 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request,
            @Parameter(description = "반복 Task 수정 범위. 반복 Task가 아니면 무시됩니다.", schema = @Schema(allowableValues = {"THIS", "THIS_AND_FUTURE", "ALL"}, example = "THIS"))
            @RequestParam(required = false) RecurrenceEditScope recurrenceScope
    ) {
        request.validate();
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.updateForOwner(id, request, owner, recurrenceScope)));
    }

    @Operation(summary = "Task Today 이동", description = "로그인 사용자의 Task를 요청 날짜의 Today로 이동합니다.")
    @PatchMapping("/{id}/today")
    public ResponseEntity<ApiResponse<TaskResponse>> moveToToday(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "Today 이동 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.moveToTodayForOwner(id, date, owner)));
    }

    @Operation(summary = "Task Inbox 이동", description = "로그인 사용자의 Task를 Inbox로 이동합니다.")
    @PatchMapping("/{id}/inbox")
    public ResponseEntity<ApiResponse<TaskResponse>> moveToInbox(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.moveToInboxForOwner(id, owner)));
    }

    @Operation(summary = "Task 완료", description = "로그인 사용자의 Task를 완료 처리합니다.")
    @PatchMapping("/{id}/done")
    public ResponseEntity<ApiResponse<TaskResponse>> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "완료 시각. 생략하면 서버 현재 시각을 사용합니다.", schema = @Schema(type = "string", format = "date-time", example = "2026-07-15T09:30:00"))
            @RequestParam(required = false) LocalDateTime completedAt
    ) {
        User owner = currentUserService.requireUser(jwt);
        LocalDateTime effectiveCompletedAt = completedAt == null ? LocalDateTime.now(Constant.ZONE) : completedAt;
        return ResponseEntity.ok(ApiResponse.success(taskService.completeForOwner(id, effectiveCompletedAt, owner)));
    }

    @Operation(summary = "Task 완료 취소", description = "완료된 Task를 요청 날짜의 Today로 되돌립니다.")
    @PatchMapping("/{id}/done/cancel")
    public ResponseEntity<ApiResponse<TaskResponse>> reopenToday(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "완료 취소 후 Today에 배치할 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.reopenTodayForOwner(id, date, owner)));
    }

    @Operation(summary = "Task 이월", description = "로그인 사용자의 Task를 요청 날짜로 이월합니다.")
    @PatchMapping("/{id}/carry-over")
    public ResponseEntity<ApiResponse<TaskResponse>> carryOver(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "이월 대상 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.carryOverForOwner(id, date, owner)));
    }

    @Operation(summary = "Today Task 한 칸 재정렬", description = "요청 날짜의 Today Task를 위 또는 아래로 한 칸 이동합니다.")
    @PatchMapping("/{id}/today-order")
    public ResponseEntity<ApiResponse<TaskResponse>> reorderToday(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "재정렬 기준 Today 날짜", schema = @Schema(type = "string", format = "date", example = "2026-07-15"))
            @RequestParam LocalDate date,
            @Parameter(description = "한 칸 이동 방향", schema = @Schema(allowableValues = {"UP", "DOWN"}, example = "UP"))
            @RequestParam TodayOrderDirection direction
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.reorderTodayForOwner(id, date, direction, owner)));
    }

    @Operation(
            summary = "Today Task 일괄 재정렬",
            description = "요청 날짜의 일정이 아닌 Today Task 전체 순서를 한 번에 저장합니다. 현재 목록과 요청 ID 집합이 다르면 409를 반환합니다."
    )
    @PutMapping("/today-order")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> reorderToday(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TodayOrderRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.reorderTodayForOwner(request, owner)));
    }

    @Operation(summary = "미룬 이유 설정", description = "로그인 사용자의 Task에 미룬 이유를 설정합니다.")
    @PatchMapping("/{id}/defer-reason")
    public ResponseEntity<ApiResponse<TaskResponse>> setDeferReason(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(
                    description = "미룬 이유",
                    schema = @Schema(
                            allowableValues = {"TOO_BIG", "NOT_NEEDED_NOW", "AVOIDING", "NO_DEADLINE", "WAITING_OTHER", "ETC"},
                            example = "TOO_BIG"
                    )
            )
            @RequestParam DeferReason reason
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.setDeferReasonForOwner(id, reason, owner)));
    }

    @Operation(summary = "미룬 이유 삭제", description = "로그인 사용자의 Task에서 미룬 이유를 삭제합니다.")
    @DeleteMapping("/{id}/defer-reason")
    public ResponseEntity<ApiResponse<TaskResponse>> clearDeferReason(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.clearDeferReasonForOwner(id, owner)));
    }

    @Operation(summary = "D-Day 목표 연결", description = "로그인 사용자의 Task를 D-Day 목표에 연결합니다.")
    @PatchMapping("/{id}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> connectDdayGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestParam Long ddayGoalId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.connectDdayGoalForOwner(id, ddayGoalId, owner)));
    }

    @Operation(summary = "D-Day 목표 연결 해제", description = "로그인 사용자의 Task에서 D-Day 목표 연결을 해제합니다.")
    @DeleteMapping("/{id}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> disconnectDdayGoal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.disconnectDdayGoalForOwner(id, owner)));
    }

    @Operation(summary = "미정 Task 조회", description = "로그인 사용자의 미정 Task 목록을 조회합니다.")
    @GetMapping("/unscheduled")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getUnscheduledTasks(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskService.getUnscheduledTasksForOwner(owner)));
    }

    @Operation(summary = "Task 삭제", description = "로그인 사용자의 Task를 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Parameter(description = "반복 Task 삭제 범위. 반복 Task가 아니면 무시됩니다.", schema = @Schema(allowableValues = {"THIS", "THIS_AND_FUTURE", "ALL"}, example = "THIS"))
            @RequestParam(required = false) RecurrenceEditScope recurrenceScope
    ) {
        User owner = currentUserService.requireUser(jwt);
        taskService.deleteForOwner(id, owner, recurrenceScope);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
