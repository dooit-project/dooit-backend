package pj.dooit.dday.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.dday.dto.DdayGoalRequest;
import pj.dooit.dday.dto.DdayGoalResponse;
import pj.dooit.dday.dto.DdayGoalTaskRequest;
import pj.dooit.dday.service.DdayGoalService;
import pj.dooit.task.service.TaskService;
import pj.dooit.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pj.dooit.task.dto.TaskResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dday-goals")
@RequiredArgsConstructor
@Tag(name = "v1 D-Day", description = "모바일 D-Day 목표 API")
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
                description = "D-Day 목표 없음",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        )
})
public class DdayGoalV1Controller {

    private final DdayGoalService ddayGoalService;
    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "D-Day 목표 생성", description = "로그인 사용자의 D-Day 목표를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DdayGoalResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DdayGoalRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        DdayGoalResponse response = ddayGoalService.createForOwner(request, owner);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "D-Day 목표 목록 조회", description = "로그인 사용자의 D-Day 목표 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DdayGoalResponse>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.findAllForOwner(owner)));
    }

    @Operation(summary = "D-Day 목표 단건 조회", description = "로그인 사용자의 D-Day 목표를 단건 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DdayGoalResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.getForOwner(id, owner)));
    }

    @Operation(summary = "D-Day 연결 Task 조회", description = "D-Day 목표에 연결된 로그인 사용자의 Task 목록을 조회합니다.")
    @GetMapping("/{id}/tasks")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> findTasks(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(ddayGoalService.findTasksForOwner(id, owner)));
    }

    @Operation(summary = "D-Day 기반 Today Task 생성", description = "D-Day 목표에 연결된 Today Task를 생성합니다.")
    @PostMapping("/{id}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> createTodayTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody DdayGoalTaskRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        TaskResponse response = taskService.createTodayTaskForDdayGoalForOwner(
                id,
                request.title(),
                request.date(),
                owner
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "D-Day 목표 삭제", description = "로그인 사용자의 D-Day 목표를 삭제하고 연결된 Task의 D-Day 연결을 해제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        ddayGoalService.deleteForOwner(id, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
