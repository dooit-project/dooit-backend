package pj.dooit.task.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.dto.TaskTemplateCreateTaskRequest;
import pj.dooit.task.dto.TaskTemplateRequest;
import pj.dooit.task.dto.TaskTemplateResponse;
import pj.dooit.task.service.TaskTemplateService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/task-templates")
@RequiredArgsConstructor
@Tag(name = "v1 Task Template", description = "모바일 Task 템플릿 API")
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
                description = "Task 템플릿 없음",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        )
})
public class TaskTemplateV1Controller {

    private final TaskTemplateService taskTemplateService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Task 템플릿 생성", description = "로그인 사용자의 Task 템플릿을 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TaskTemplateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskTemplateService.createForOwner(request, owner)));
    }

    @Operation(summary = "Task 템플릿 목록 조회", description = "로그인 사용자의 Task 템플릿 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskTemplateResponse>>> findAll(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskTemplateService.findAllForOwner(owner)));
    }

    @Operation(summary = "Task 템플릿 단건 조회", description = "로그인 사용자의 Task 템플릿을 단건 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskTemplateService.getForOwner(id, owner)));
    }

    @Operation(summary = "Task 템플릿 수정", description = "로그인 사용자의 Task 템플릿을 수정합니다.")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TaskTemplateRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(taskTemplateService.updateForOwner(id, request, owner)));
    }

    @Operation(summary = "Task 템플릿 삭제", description = "로그인 사용자의 Task 템플릿을 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        taskTemplateService.deleteForOwner(id, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Task 템플릿 기반 Task 생성", description = "로그인 사용자의 Task 템플릿으로 Task를 생성합니다.")
    @PostMapping("/{id}/tasks")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody TaskTemplateCreateTaskRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(taskTemplateService.createTaskForOwner(id, request, owner)));
    }
}
