package pj.dooit.task.controller;

import pj.dooit.Constant;
import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.task.dto.TaskChecklistItemOrderRequest;
import pj.dooit.task.dto.TaskChecklistItemRequest;
import pj.dooit.task.dto.TaskChecklistItemResponse;
import pj.dooit.task.service.TaskChecklistItemService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/checklist-items")
@RequiredArgsConstructor
@Tag(name = "v1 Task Checklist", description = "모바일 Task checklist API")
@SecurityRequirement(name = "bearerAuth")
public class TaskChecklistItemV1Controller {

    private final TaskChecklistItemService checklistItemService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "Task checklist item 목록 조회", description = "로그인 사용자의 Task checklist item 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskChecklistItemResponse>>> findAll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(checklistItemService.findAllForOwner(taskId, owner)));
    }

    @Operation(summary = "Task checklist item 생성", description = "로그인 사용자의 Task에 checklist item을 추가합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskChecklistItemRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(checklistItemService.createForOwner(taskId, request, owner)));
    }

    @Operation(summary = "Task checklist item 수정", description = "checklist item 제목을 수정합니다.")
    @PutMapping("/{itemId}")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @PathVariable Long itemId,
            @Valid @RequestBody TaskChecklistItemRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(checklistItemService.updateForOwner(taskId, itemId, request, owner)));
    }

    @Operation(summary = "Task checklist item 완료", description = "checklist item을 완료 처리합니다.")
    @PatchMapping("/{itemId}/done")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @PathVariable Long itemId,
            @RequestParam(required = false) LocalDateTime completedAt
    ) {
        User owner = currentUserService.requireUser(jwt);
        LocalDateTime effectiveCompletedAt = completedAt == null ? LocalDateTime.now(Constant.ZONE) : completedAt;
        return ResponseEntity.ok(ApiResponse.success(checklistItemService.completeForOwner(taskId, itemId, effectiveCompletedAt, owner)));
    }

    @Operation(summary = "Task checklist item 완료 취소", description = "checklist item을 미완료 상태로 되돌립니다.")
    @PatchMapping("/{itemId}/done/cancel")
    public ResponseEntity<ApiResponse<TaskChecklistItemResponse>> reopen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @PathVariable Long itemId
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(checklistItemService.reopenForOwner(taskId, itemId, owner)));
    }

    @Operation(summary = "Task checklist item 삭제", description = "checklist item을 삭제합니다.")
    @DeleteMapping("/{itemId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @PathVariable Long itemId
    ) {
        User owner = currentUserService.requireUser(jwt);
        checklistItemService.deleteForOwner(taskId, itemId, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "Task checklist item 일괄 재정렬", description = "현재 Task의 checklist item 전체 순서를 한 번에 저장합니다.")
    @PutMapping("/order")
    public ResponseEntity<ApiResponse<List<TaskChecklistItemResponse>>> reorder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long taskId,
            @Valid @RequestBody TaskChecklistItemOrderRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(checklistItemService.reorderForOwner(taskId, request, owner)));
    }
}
