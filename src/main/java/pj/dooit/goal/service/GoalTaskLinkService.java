package pj.dooit.goal.service;

import pj.dooit.common.domain.ResourceScope;
import pj.dooit.goal.dto.GoalTaskCreateRequest;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalTaskConflictException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.exception.TaskNotFoundException;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.personalsystem.repository.PersonalSystemOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoalTaskLinkService {
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final GoalPlanOccurrenceRepository occurrenceRepository;
    private final PersonalSystemOccurrenceRepository personalOccurrenceRepository;

    @Transactional
    public TaskResponse create(Long goalId, GoalTaskCreateRequest request, User owner) {
        requireGoal(goalId, owner);
        Task task = Task.builder().owner(owner).title(request.title()).type(TaskType.TODO).build();
        task.connectGoal(goalId);
        task.moveToToday(request.date());
        Integer maxOrder = taskRepository.findMaxTodayOrder(owner.getId(), request.date());
        task.assignTodayOrder(maxOrder == null ? 0 : maxOrder + 1);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse link(Long taskId, Long goalId, User owner) {
        Task task = requireTask(taskId, owner);
        validateStandalone(task);
        if (goalId != null) {
            requireGoal(goalId, owner);
        }
        task.connectGoal(goalId);
        return TaskResponse.from(task);
    }

    @Transactional
    public void applyRequest(Task task, TaskRequest request) {
        if (request.linkedGoalId() == null) {
            return;
        }
        if (request.recurrence() != null) {
            throw new GoalValidationException("반복 Task에는 일회성 목표 연결을 사용할 수 없습니다.");
        }
        validateStandalone(task);
        requireGoal(request.linkedGoalId(), task.getOwner());
        task.connectGoal(request.linkedGoalId());
    }

    private Task requireTask(Long id, User owner) {
        // Match the occurrence-before-task lock order of completion and result mutations.
        occurrenceRepository.findByLinkedTaskId(id);
        return taskRepository.findForGoalLinkUpdate(id, owner.getId())
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void validateStandalone(Task task) {
        if (task.getScope() != ResourceScope.PERSONAL || task.getOwner() == null) {
            throw new GoalValidationException("개인 Task만 개인 목표에 연결할 수 있습니다.");
        }
        if (task.getRecurrenceSeries() != null || (task.getId() != null
                && (occurrenceRepository.findByLinkedTaskId(task.getId()).isPresent()
                || personalOccurrenceRepository.findByLinkedTaskIdAndOwnerId(task.getId(), task.getOwner().getId()).isPresent()))) {
            throw new GoalTaskConflictException("반복 회차 Task의 목표는 계획에서 변경해주세요.");
        }
    }

    private void requireGoal(Long id, User owner) {
        if (owner == null || !goalRepository.existsByIdAndOwnerId(id, owner.getId())) {
            throw new GoalNotFoundException("goalId=" + id);
        }
    }
}
