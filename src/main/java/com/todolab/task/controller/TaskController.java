package com.todolab.task.controller;

import com.todolab.Constant;
import com.todolab.common.api.ApiResponse;
import com.todolab.task.domain.DeferReason;
import com.todolab.task.domain.TodayOrderDirection;
import com.todolab.task.dto.TaskCategoryGroupResponse;
import com.todolab.task.dto.TaskQueryRequest;
import com.todolab.task.dto.TaskRecommendationResponse;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(@Valid @RequestBody TaskRequest request) {
        log.info("[API] createTask request :: allDay={}", request.allDay());

        request.validate();

        TaskResponse res = taskService.create(request);

        log.info("[API] createTask success :: id={}", res.id());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(@PathVariable Long id) {
        log.info("[API] getTask request :: id={}", id);
        TaskResponse res = taskService.getTask(id);
        log.info("[API] getTask success :: id={}", id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String date
    ) {
        log.info("[API] getTasks :: Type : {}, TaskType : {}, Date : {}", type, taskType, date);
        TaskQueryRequest request = TaskQueryRequest.builder()
                .rawType(type)
                .rawTaskType(taskType)
                .rawDate(date)
                .build();

        List<TaskResponse> res = taskService.getTasks(request);
        log.info("[API] getTasks success :: type={}, taskType={}, date={}, taskCount={}", type, taskType, date, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/grouped")
    public ResponseEntity<ApiResponse<List<TaskCategoryGroupResponse>>> getGroupedTasks(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String date
    ) {
        log.info("[API] getGroupedTasks :: Type : {}, TaskType : {}, Date : {}", type, taskType, date);
        TaskQueryRequest request = TaskQueryRequest.builder()
                .rawType(type)
                .rawTaskType(taskType)
                .rawDate(date)
                .build();

        List<TaskCategoryGroupResponse> res = taskService.getGroupedTasks(request);
        log.info("[API] getGroupedTasks success :: type={}, taskType={}, date={}, groupCount={}", type, taskType, date, res.size());
        log.debug("[API] getGroupedTasks categories :: {}",
                res.stream().map(TaskCategoryGroupResponse::category).toList());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getInboxTasks() {
        log.info("[API] getInboxTasks request");

        List<TaskResponse> res = taskService.getInboxTasks();

        log.info("[API] getInboxTasks success :: taskCount={}", res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping({"/recommendations/today", "/today/recommendations"})
    public ResponseEntity<ApiResponse<List<TaskRecommendationResponse>>> getTodayRecommendations(
            @RequestParam LocalDate date
    ) {
        log.info("[API] getTodayRecommendations request :: date={}", date);

        List<TaskRecommendationResponse> res = taskService.getTodayRecommendations(date);

        log.info("[API] getTodayRecommendations success :: date={}, recommendationCount={}", date, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTodayTasks(
            @RequestParam LocalDate date
    ) {
        log.info("[API] getTodayTasks request :: date={}", date);

        List<TaskResponse> res = taskService.getTodayTasks(date);

        log.info("[API] getTodayTasks success :: date={}, taskCount={}", date, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getOverdueTasks(
            @RequestParam LocalDate date
    ) {
        log.info("[API] getOverdueTasks request :: date={}", date);

        List<TaskResponse> res = taskService.getOverdueTasks(date);

        log.info("[API] getOverdueTasks success :: date={}, taskCount={}", date, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/stale")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getStaleTasks(
            @RequestParam(required = false) LocalDate date
    ) {
        LocalDate referenceDate = date == null ? LocalDate.now(Constant.ZONE) : date;
        log.info("[API] getStaleTasks request :: date={}", referenceDate);

        List<TaskResponse> res = taskService.getOverdueTasks(referenceDate);

        log.info("[API] getStaleTasks success :: date={}, taskCount={}", referenceDate, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/done")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getDoneTasks(
            @RequestParam LocalDate date
    ) {
        log.info("[API] getDoneTasks request :: date={}", date);

        List<TaskResponse> res = taskService.getDoneTasks(date);

        log.info("[API] getDoneTasks success :: date={}, taskCount={}", date, res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskRequest request
    ) {
        log.info("[API] updateTask request :: id={}, allDay={}", id, request.allDay());

        request.validate();

        TaskResponse res = taskService.update(id, request);
        log.info("[API] updateTask success :: id={}", id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/today")
    public ResponseEntity<ApiResponse<TaskResponse>> moveToToday(
            @PathVariable Long id,
            @RequestParam LocalDate date
    ) {
        log.info("[API] moveToToday request :: id={}, date={}", id, date);

        TaskResponse res = taskService.moveToToday(id, date);

        log.info("[API] moveToToday success :: id={}, date={}", id, date);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/inbox")
    public ResponseEntity<ApiResponse<TaskResponse>> moveToInbox(
            @PathVariable Long id
    ) {
        log.info("[API] moveToInbox request :: id={}", id);

        TaskResponse res = taskService.moveToInbox(id);

        log.info("[API] moveToInbox success :: id={}", id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/done")
    public ResponseEntity<ApiResponse<TaskResponse>> complete(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDateTime completedAt
    ) {
        LocalDateTime effectiveCompletedAt = completedAt == null ? LocalDateTime.now(Constant.ZONE) : completedAt;
        log.info("[API] complete request :: id={}, completedAt={}", id, effectiveCompletedAt);

        TaskResponse res = taskService.complete(id, effectiveCompletedAt);

        log.info("[API] complete success :: id={}, completedAt={}", id, effectiveCompletedAt);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/done/cancel")
    public ResponseEntity<ApiResponse<TaskResponse>> reopenToday(
            @PathVariable Long id,
            @RequestParam LocalDate date
    ) {
        log.info("[API] reopenToday request :: id={}, date={}", id, date);

        TaskResponse res = taskService.reopenToday(id, date);

        log.info("[API] reopenToday success :: id={}, date={}", id, date);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/carry-over")
    public ResponseEntity<ApiResponse<TaskResponse>> carryOver(
            @PathVariable Long id,
            @RequestParam LocalDate date
    ) {
        log.info("[API] carryOver request :: id={}, date={}", id, date);

        TaskResponse res = taskService.carryOver(id, date);

        log.info("[API] carryOver success :: id={}, date={}", id, date);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/today-order")
    public ResponseEntity<ApiResponse<TaskResponse>> reorderToday(
            @PathVariable Long id,
            @RequestParam LocalDate date,
            @RequestParam TodayOrderDirection direction
    ) {
        log.info("[API] reorderToday request :: id={}, date={}, direction={}", id, date, direction);

        TaskResponse res = taskService.reorderToday(id, date, direction);

        log.info("[API] reorderToday success :: id={}, date={}, direction={}", id, date, direction);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/defer-reason")
    public ResponseEntity<ApiResponse<TaskResponse>> setDeferReason(
            @PathVariable Long id,
            @RequestParam DeferReason reason
    ) {
        log.info("[API] setDeferReason request :: id={}, reason={}", id, reason);

        TaskResponse res = taskService.setDeferReason(id, reason);

        log.info("[API] setDeferReason success :: id={}, reason={}", id, reason);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @DeleteMapping("/{id}/defer-reason")
    public ResponseEntity<ApiResponse<TaskResponse>> clearDeferReason(@PathVariable Long id) {
        log.info("[API] clearDeferReason request :: id={}", id);

        TaskResponse res = taskService.clearDeferReason(id);

        log.info("[API] clearDeferReason success :: id={}", id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @PatchMapping("/{id}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> connectDdayGoal(
            @PathVariable Long id,
            @RequestParam Long ddayGoalId
    ) {
        log.info("[API] connectDdayGoal request :: id={}, ddayGoalId={}", id, ddayGoalId);

        TaskResponse res = taskService.connectDdayGoal(id, ddayGoalId);

        log.info("[API] connectDdayGoal success :: id={}, ddayGoalId={}", id, ddayGoalId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @DeleteMapping("/{id}/dday-goal")
    public ResponseEntity<ApiResponse<TaskResponse>> disconnectDdayGoal(@PathVariable Long id) {
        log.info("[API] disconnectDdayGoal request :: id={}", id);

        TaskResponse res = taskService.disconnectDdayGoal(id);

        log.info("[API] disconnectDdayGoal success :: id={}", id);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/unscheduled")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getUnscheduledTasks() {
        log.info("[API] getUnscheduledTasks request");

        List<TaskResponse> res = taskService.getUnscheduledTasks();

        log.info("[API] getUnscheduledTasks success :: taskCount={}", res.size());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @GetMapping("/unscheduled/grouped")
    public ResponseEntity<ApiResponse<List<TaskCategoryGroupResponse>>> getGroupedUnscheduledTasks() {
        log.info("[API] getGroupedUnscheduledTasks request");

        List<TaskCategoryGroupResponse> res = taskService.getGroupedUnscheduledTasks();

        log.info("[API] getGroupedUnscheduledTasks success :: groupCount={}", res.size());
        log.debug("[API] getGroupedUnscheduledTasks categories :: {}",
                res.stream().map(TaskCategoryGroupResponse::category).toList());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(res));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable Long id) {
        log.info("[API] deleteTask request :: id={}", id);

        taskService.delete(id);
        log.info("[API] deleteTask success :: id={}", id);

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(null));
    }
}
