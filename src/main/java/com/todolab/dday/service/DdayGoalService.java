package com.todolab.dday.service;

import com.todolab.dday.domain.DdayGoal;
import com.todolab.common.domain.ResourceScope;
import com.todolab.dday.dto.DdayGoalRequest;
import com.todolab.dday.dto.DdayGoalResponse;
import com.todolab.dday.exception.DdayGoalNotFoundException;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.task.domain.Task;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.workspace.domain.SharedWorkspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DdayGoalService {

    private final DdayGoalRepository ddayGoalRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public DdayGoalResponse create(DdayGoalRequest request) {
        return create(request, null);
    }

    @Transactional
    public DdayGoalResponse createForOwner(DdayGoalRequest request, User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        return create(request, owner);
    }

    @Transactional
    public DdayGoalResponse createForWorkspace(DdayGoalRequest request, User actor, SharedWorkspace workspace) {
        if (actor == null) {
            throw new IllegalArgumentException("actor는 필수입니다.");
        }
        if (workspace == null || workspace.getId() == null) {
            throw new IllegalArgumentException("workspace는 영속화된 workspace여야 합니다.");
        }
        DdayGoal goal = new DdayGoal(request.title(), request.targetDate(), actor);
        goal.assignWorkspace(workspace);
        return DdayGoalResponse.from(ddayGoalRepository.save(goal));
    }

    private DdayGoalResponse create(DdayGoalRequest request, User owner) {
        DdayGoal saved = ddayGoalRepository.save(new DdayGoal(request.title(), request.targetDate(), owner));
        return DdayGoalResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DdayGoalResponse> findAll() {
        return ddayGoalRepository.findAllByOrderByTargetDateAscIdAsc().stream()
                .map(DdayGoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DdayGoalResponse> findAllForOwner(User owner) {
        return ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(ownerId(owner)).stream()
                .map(DdayGoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DdayGoalResponse getForOwner(Long id, User owner) {
        DdayGoal goal = ddayGoalRepository.findByIdAndOwnerId(id, ownerId(owner))
                .orElseThrow(() -> new DdayGoalNotFoundException(id));
        return DdayGoalResponse.from(goal);
    }

    @Transactional(readOnly = true)
    public DdayGoalResponse getForWorkspace(Long id, SharedWorkspace workspace) {
        DdayGoal goal = ddayGoalRepository.findByIdAndWorkspaceIdAndScope(id, workspace.getId(), ResourceScope.WORKSPACE)
                .orElseThrow(() -> new DdayGoalNotFoundException(id));
        return DdayGoalResponse.from(goal);
    }

    @Transactional(readOnly = true)
    public List<DdayGoalResponse> findAllForWorkspace(SharedWorkspace workspace) {
        return ddayGoalRepository.findAllByWorkspaceIdAndScopeOrderByTargetDateAscIdAsc(
                        workspace.getId(),
                        ResourceScope.WORKSPACE
                ).stream()
                .map(DdayGoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DdayGoalResponse> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return ddayGoalRepository.findByTargetDateBetweenOrderByTargetDateAscIdAsc(startDate, endDate).stream()
                .map(DdayGoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DdayGoalResponse> findByDateRangeForOwner(LocalDate startDate, LocalDate endDate, User owner) {
        return ddayGoalRepository.findByOwnerIdAndTargetDateBetweenOrderByTargetDateAscIdAsc(ownerId(owner), startDate, endDate).stream()
                .map(DdayGoalResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findTasks(Long id) {
        if (!ddayGoalRepository.existsById(id)) {
            throw new DdayGoalNotFoundException(id);
        }

        return taskRepository.findByDdayGoalId(id).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findTasksForOwner(Long id, User owner) {
        Long ownerId = ownerId(owner);
        if (!ddayGoalRepository.existsByIdAndOwnerId(id, ownerId)) {
            throw new DdayGoalNotFoundException(id);
        }

        return taskRepository.findByDdayGoalId(ownerId, id).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findTasksForWorkspace(Long id, SharedWorkspace workspace) {
        Long workspaceId = workspaceId(workspace);
        if (ddayGoalRepository.findByIdAndWorkspaceIdAndScope(id, workspaceId, ResourceScope.WORKSPACE).isEmpty()) {
            throw new DdayGoalNotFoundException(id);
        }

        return taskRepository.findWorkspaceByDdayGoalId(workspaceId, id).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        if (!ddayGoalRepository.existsById(id)) {
            throw new DdayGoalNotFoundException(id);
        }
        taskRepository.findByDdayGoalId(id)
                .forEach(Task::disconnectDdayGoal);
        ddayGoalRepository.deleteById(id);
    }

    @Transactional
    public void deleteForOwner(Long id, User owner) {
        Long ownerId = ownerId(owner);
        if (!ddayGoalRepository.existsByIdAndOwnerId(id, ownerId)) {
            throw new DdayGoalNotFoundException(id);
        }
        taskRepository.findByDdayGoalId(ownerId, id)
                .forEach(Task::disconnectDdayGoal);
        ddayGoalRepository.deleteById(id);
    }

    @Transactional
    public void deleteForWorkspace(Long id, SharedWorkspace workspace) {
        Long workspaceId = workspaceId(workspace);
        if (ddayGoalRepository.findByIdAndWorkspaceIdAndScope(id, workspaceId, ResourceScope.WORKSPACE).isEmpty()) {
            throw new DdayGoalNotFoundException(id);
        }
        taskRepository.findWorkspaceByDdayGoalId(workspaceId, id)
                .forEach(Task::disconnectDdayGoal);
        ddayGoalRepository.deleteById(id);
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
}
