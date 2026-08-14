package com.todolab.task.service;

import com.todolab.common.domain.ResourceScope;
import com.todolab.dday.domain.DdayGoal;
import com.todolab.dday.exception.DdayGoalNotFoundException;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.task.domain.DeferReason;
import com.todolab.task.domain.RecurrenceEditScope;
import com.todolab.task.domain.RecurrenceExceptionType;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;
import com.todolab.task.domain.TodayOrderDirection;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TodayOrderRequest;
import com.todolab.task.exception.TaskOrderConflictException;
import com.todolab.task.exception.TaskNotFoundException;
import com.todolab.task.exception.TaskValidationException;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.workspace.domain.SharedWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskTxService {

    private final TaskRepository taskRepository;
    private final DdayGoalRepository ddayGoalRepository;

    @Transactional
    public Task updateTx(Long id, TaskRequest req) {
        validateNoRecurrenceRuleUpdate(req);
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        task.update(req.title(), req.description(), req.normalizedType(), req.startAt(), req.endAt(), req.allDay(), req.category());
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTxForOwner(Long id, TaskRequest req, User owner) {
        return updateTxForOwner(id, req, owner, RecurrenceEditScope.THIS);
    }

    @Transactional
    public Task updateTxForOwner(Long id, TaskRequest req, User owner, RecurrenceEditScope recurrenceScope) {
        validateNoRecurrenceRuleUpdate(req);
        Task task = findTaskForOwner(id, owner);
        RecurrenceEditScope effectiveScope = normalizeScope(recurrenceScope);
        if (!isRecurringOccurrence(task) || effectiveScope == RecurrenceEditScope.THIS) {
            applySingleUpdate(task, req);
            return taskRepository.save(task);
        }

        List<Task> scopedTasks = scopedRecurringTasks(task, ownerId(owner), effectiveScope);
        scopedTasks.forEach(scopedTask -> applyScopedOccurrenceUpdate(scopedTask, req));
        taskRepository.saveAll(scopedTasks);
        return scopedTasks.stream()
                .filter(scopedTask -> id.equals(scopedTask.getId()))
                .findFirst()
                .orElse(task);
    }

    @Transactional
    public Task updateTxForWorkspace(Long id, TaskRequest req, SharedWorkspace workspace) {
        validateNoRecurrenceRuleUpdate(req);
        Task task = findTaskForWorkspace(id, workspace);
        if (isRecurringOccurrence(task)) {
            throw new TaskValidationException("workspace 반복 Task 수정은 아직 지원하지 않습니다.");
        }

        applySingleUpdate(task, req);
        return taskRepository.save(task);
    }

    @Transactional
    public Task moveToTodayTx(Long id, LocalDate targetDate) {
        Task task = findTask(id);
        task.moveToToday(targetDate);
        assignLastTodayOrder(task, targetDate);
        return taskRepository.save(task);
    }

    @Transactional
    public Task moveToTodayTxForOwner(Long id, LocalDate targetDate, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.moveToToday(targetDate);
        assignLastTodayOrder(task, targetDate, ownerId(owner));
        return taskRepository.save(task);
    }

    @Transactional
    public Task moveToInboxTx(Long id) {
        Task task = findTask(id);
        task.moveToInbox();
        return taskRepository.save(task);
    }

    @Transactional
    public Task moveToInboxTxForOwner(Long id, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.moveToInbox();
        return taskRepository.save(task);
    }

    @Transactional
    public Task completeTx(Long id, LocalDateTime completedAt) {
        Task task = findTask(id);
        task.complete(completedAt);
        return taskRepository.save(task);
    }

    @Transactional
    public Task completeTxForOwner(Long id, LocalDateTime completedAt, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.complete(completedAt);
        return taskRepository.save(task);
    }

    @Transactional
    public Task reopenTodayTx(Long id, LocalDate targetDate) {
        Task task = findTask(id);
        task.reopenToday(targetDate);
        assignLastTodayOrder(task, targetDate);
        return taskRepository.save(task);
    }

    @Transactional
    public Task reopenTodayTxForOwner(Long id, LocalDate targetDate, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.reopenToday(targetDate);
        assignLastTodayOrder(task, targetDate, ownerId(owner));
        return taskRepository.save(task);
    }

    @Transactional
    public Task carryOverTx(Long id, LocalDate nextDate) {
        Task task = findTask(id);
        task.carryOverTo(nextDate);
        assignLastTodayOrder(task, nextDate);
        return taskRepository.save(task);
    }

    @Transactional
    public Task carryOverTxForOwner(Long id, LocalDate nextDate, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.carryOverTo(nextDate);
        assignLastTodayOrder(task, nextDate, ownerId(owner));
        return taskRepository.save(task);
    }

    @Transactional
    public Task reorderTodayTx(Long id, LocalDate targetDate, TodayOrderDirection direction) {
        Task task = findTask(id);
        validateTodayOrderTarget(task, targetDate, direction);

        List<Task> tasks = taskRepository.findPlannedTasks(targetDate, targetDate.plusDays(1));
        int currentIndex = findTaskIndex(tasks, id);
        int nextIndex = direction == TodayOrderDirection.UP ? currentIndex - 1 : currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= tasks.size()) {
            return task;
        }

        normalizeTodayOrder(tasks);
        Task target = tasks.get(currentIndex);
        Task neighbor = tasks.get(nextIndex);
        int targetOrder = target.getTodayOrder();
        target.assignTodayOrder(neighbor.getTodayOrder());
        neighbor.assignTodayOrder(targetOrder);
        taskRepository.saveAll(tasks);
        return target;
    }

    @Transactional
    public Task reorderTodayTxForOwner(Long id, LocalDate targetDate, TodayOrderDirection direction, User owner) {
        Task task = findTaskForOwner(id, owner);
        validateTodayOrderTarget(task, targetDate, direction);

        List<Task> tasks = taskRepository.findPlannedTasks(ownerId(owner), targetDate, targetDate.plusDays(1));
        int currentIndex = findTaskIndex(tasks, id);
        int nextIndex = direction == TodayOrderDirection.UP ? currentIndex - 1 : currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= tasks.size()) {
            return task;
        }

        normalizeTodayOrder(tasks);
        Task target = tasks.get(currentIndex);
        Task neighbor = tasks.get(nextIndex);
        int targetOrder = target.getTodayOrder();
        target.assignTodayOrder(neighbor.getTodayOrder());
        neighbor.assignTodayOrder(targetOrder);
        taskRepository.saveAll(tasks);
        return target;
    }

    @Transactional
    public List<Task> reorderTodayTxForOwner(TodayOrderRequest request, User owner) {
        validateBulkTodayOrderRequest(request);
        Long ownerId = ownerId(owner);

        List<Task> currentTasks = taskRepository.findReorderableTodayTasks(ownerId, request.date());
        validateSameTodayOrderSet(currentTasks, request.orderedTaskIds());

        Map<Long, Task> taskById = currentTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        List<Task> reordered = request.orderedTaskIds().stream()
                .map(taskById::get)
                .toList();

        normalizeTodayOrder(reordered);
        taskRepository.saveAll(reordered);
        return reordered;
    }

    @Transactional
    public Task setDeferReasonTx(Long id, DeferReason reason) {
        Task task = findTask(id);
        task.setDeferReason(reason);
        return taskRepository.save(task);
    }

    @Transactional
    public Task setDeferReasonTxForOwner(Long id, DeferReason reason, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.setDeferReason(reason);
        return taskRepository.save(task);
    }

    @Transactional
    public Task clearDeferReasonTx(Long id) {
        Task task = findTask(id);
        task.clearDeferReason();
        return taskRepository.save(task);
    }

    @Transactional
    public Task clearDeferReasonTxForOwner(Long id, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.clearDeferReason();
        return taskRepository.save(task);
    }

    @Transactional
    public Task connectDdayGoalTx(Long id, Long ddayGoalId) {
        Task task = findTask(id);
        DdayGoal ddayGoal = ddayGoalRepository.findById(ddayGoalId)
                .orElseThrow(() -> new DdayGoalNotFoundException(ddayGoalId));

        task.connectDdayGoal(ddayGoal);
        return taskRepository.save(task);
    }

    @Transactional
    public Task connectDdayGoalTxForOwner(Long id, Long ddayGoalId, User owner) {
        Task task = findTaskForOwner(id, owner);
        DdayGoal ddayGoal = ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, ownerId(owner))
                .orElseThrow(() -> new DdayGoalNotFoundException(ddayGoalId));

        task.connectDdayGoal(ddayGoal);
        return taskRepository.save(task);
    }

    @Transactional
    public Task disconnectDdayGoalTx(Long id) {
        Task task = findTask(id);
        task.disconnectDdayGoal();
        return taskRepository.save(task);
    }

    @Transactional
    public Task disconnectDdayGoalTxForOwner(Long id, User owner) {
        Task task = findTaskForOwner(id, owner);
        task.disconnectDdayGoal();
        return taskRepository.save(task);
    }

    @Transactional
    public Task createTodayTaskForDdayGoalTxForOwner(Long ddayGoalId, String title, LocalDate targetDate, User owner) {
        Long ownerId = ownerId(owner);
        DdayGoal ddayGoal = ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, ownerId)
                .orElseThrow(() -> new DdayGoalNotFoundException(ddayGoalId));

        Task task = Task.builder()
                .title(title)
                .type(TaskType.TODO)
                .owner(owner)
                .ddayGoal(ddayGoal)
                .build();
        task.moveToToday(targetDate);
        assignLastTodayOrder(task, targetDate, ownerId);
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTxForOwner(Long id, User owner, RecurrenceEditScope recurrenceScope) {
        Task task = findTaskForOwner(id, owner);
        RecurrenceEditScope effectiveScope = normalizeScope(recurrenceScope);
        if (!isRecurringOccurrence(task)) {
            taskRepository.deleteById(id);
            return;
        }

        List<Task> scopedTasks = effectiveScope == RecurrenceEditScope.THIS
                ? List.of(task)
                : scopedRecurringTasks(task, ownerId(owner), effectiveScope);
        scopedTasks.forEach(this::skipRecurringOccurrence);
        truncateSeriesIfNeeded(task, effectiveScope);
        taskRepository.saveAll(scopedTasks);
    }

    @Transactional
    public void deleteTxForWorkspace(Long id, SharedWorkspace workspace) {
        Task task = findTaskForWorkspace(id, workspace);
        if (isRecurringOccurrence(task)) {
            throw new TaskValidationException("workspace 반복 Task 삭제는 아직 지원하지 않습니다.");
        }

        taskRepository.deleteById(id);
    }

    private Task findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task findTaskForOwner(Long id, User owner) {
        return taskRepository.findByIdAndOwnerId(id, ownerId(owner))
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private Task findTaskForWorkspace(Long id, SharedWorkspace workspace) {
        return taskRepository.findByIdAndWorkspaceIdAndScope(id, workspaceId(workspace), ResourceScope.WORKSPACE)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void assignLastTodayOrder(Task task, LocalDate targetDate) {
        Integer maxOrder = taskRepository.findMaxTodayOrder(targetDate);
        task.assignTodayOrder(maxOrder == null ? 0 : maxOrder + 1);
    }

    private void assignLastTodayOrder(Task task, LocalDate targetDate, Long ownerId) {
        Integer maxOrder = taskRepository.findMaxTodayOrder(ownerId, targetDate);
        task.assignTodayOrder(maxOrder == null ? 0 : maxOrder + 1);
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }

    private Long workspaceId(SharedWorkspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            throw new IllegalArgumentException("workspace는 영속화된 workspace여야 합니다.");
        }
        return workspace.getId();
    }

    private void validateTodayOrderTarget(Task task, LocalDate targetDate, TodayOrderDirection direction) {
        if (targetDate == null) {
            throw new TaskValidationException("실행 순서를 변경할 날짜가 필요합니다.");
        }
        if (direction == null) {
            throw new TaskValidationException("실행 순서 변경 방향이 필요합니다.");
        }
        if (task.getStatus() != TaskStatus.TODAY || !targetDate.equals(task.getPlannedDate())) {
            throw new TaskValidationException("해당 날짜의 Today Task만 실행 순서를 변경할 수 있습니다.");
        }
    }

    private int findTaskIndex(List<Task> tasks, Long id) {
        for (int i = 0; i < tasks.size(); i++) {
            if (id.equals(tasks.get(i).getId())) {
                return i;
            }
        }
        throw new TaskValidationException("해당 날짜의 Today 목록에서 Task를 찾을 수 없습니다.");
    }

    private void normalizeTodayOrder(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).assignTodayOrder(i);
        }
    }

    private RecurrenceEditScope normalizeScope(RecurrenceEditScope recurrenceScope) {
        return recurrenceScope == null ? RecurrenceEditScope.THIS : recurrenceScope;
    }

    private boolean isRecurringOccurrence(Task task) {
        return task.getRecurrenceSeries() != null && task.getOccurrenceDate() != null;
    }

    private void applySingleUpdate(Task task, TaskRequest req) {
        task.update(req.title(), req.description(), req.normalizedType(), req.startAt(), req.endAt(), req.allDay(), req.category());
        if (isRecurringOccurrence(task)) {
            task.markRecurrenceException(RecurrenceExceptionType.MODIFIED, originalOccurrenceDate(task));
        }
    }

    private void applyScopedOccurrenceUpdate(Task task, TaskRequest req) {
        TaskRequest occurrenceRequest = requestForOccurrence(req, task.getOccurrenceDate());
        task.update(
                occurrenceRequest.title(),
                occurrenceRequest.description(),
                occurrenceRequest.normalizedType(),
                occurrenceRequest.startAt(),
                occurrenceRequest.endAt(),
                occurrenceRequest.allDay(),
                occurrenceRequest.category()
        );
    }

    private TaskRequest requestForOccurrence(TaskRequest req, LocalDate occurrenceDate) {
        if (req.startAt() == null) {
            throw new TaskValidationException("반복 범위 수정은 시작 일시가 필요합니다.");
        }
        LocalDate requestDate = req.startAt().toLocalDate();
        long daysToMove = java.time.temporal.ChronoUnit.DAYS.between(requestDate, occurrenceDate);
        LocalDateTime startAt = req.startAt().plusDays(daysToMove);
        LocalDateTime endAt = req.endAt() == null ? null : req.endAt().plusDays(daysToMove);
        return new TaskRequest(req.title(), req.description(), req.type(), startAt, endAt, req.category(), req.allDay());
    }

    private LocalDate originalOccurrenceDate(Task task) {
        return task.getOriginalOccurrenceDate() == null ? task.getOccurrenceDate() : task.getOriginalOccurrenceDate();
    }

    private List<Task> scopedRecurringTasks(Task task, Long ownerId, RecurrenceEditScope recurrenceScope) {
        Long recurrenceSeriesId = task.getRecurrenceSeries().getId();
        if (recurrenceScope == RecurrenceEditScope.ALL) {
            return taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(recurrenceSeriesId, ownerId);
        }
        return taskRepository.findByRecurrenceSeriesIdAndOwnerIdAndOccurrenceDateGreaterThanEqualOrderByOccurrenceDateAscIdAsc(
                recurrenceSeriesId,
                ownerId,
                task.getOccurrenceDate()
        );
    }

    private void skipRecurringOccurrence(Task task) {
        task.moveToInbox();
        task.markRecurrenceException(RecurrenceExceptionType.SKIPPED, originalOccurrenceDate(task));
    }

    private void truncateSeriesIfNeeded(Task task, RecurrenceEditScope recurrenceScope) {
        if (recurrenceScope == RecurrenceEditScope.THIS) {
            return;
        }

        LocalDate truncateBeforeDate = recurrenceScope == RecurrenceEditScope.ALL
                ? task.getRecurrenceSeries().getRecurrenceStartAt().toLocalDate()
                : task.getOccurrenceDate();
        task.getRecurrenceSeries().truncateBefore(truncateBeforeDate);
    }

    private void validateBulkTodayOrderRequest(TodayOrderRequest request) {
        if (request == null || request.date() == null) {
            throw new TaskValidationException("실행 순서를 변경할 날짜가 필요합니다.");
        }
        if (request.orderedTaskIds() == null || request.orderedTaskIds().isEmpty()) {
            throw new TaskValidationException("실행 순서를 저장할 Task 목록이 필요합니다.");
        }
        if (request.orderedTaskIds().stream().anyMatch(id -> id == null)) {
            throw new TaskValidationException("실행 순서를 저장할 Task ID는 필수입니다.");
        }
        if (new LinkedHashSet<>(request.orderedTaskIds()).size() != request.orderedTaskIds().size()) {
            throw new TaskValidationException("실행 순서를 저장할 Task ID가 중복되었습니다.");
        }
    }

    private void validateSameTodayOrderSet(List<Task> currentTasks, List<Long> orderedTaskIds) {
        List<Long> currentIds = currentTasks.stream()
                .map(Task::getId)
                .toList();
        if (!new LinkedHashSet<>(currentIds).equals(new LinkedHashSet<>(orderedTaskIds))) {
            throw new TaskOrderConflictException("요청한 Today Task 목록이 현재 재정렬 대상과 일치하지 않습니다.");
        }
    }

    private void validateNoRecurrenceRuleUpdate(TaskRequest req) {
        if (req.recurrence() != null) {
            throw new TaskValidationException("반복 규칙 수정은 아직 지원하지 않습니다.");
        }
    }
}
