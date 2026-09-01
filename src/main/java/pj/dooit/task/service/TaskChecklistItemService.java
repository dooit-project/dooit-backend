package pj.dooit.task.service;

import pj.dooit.common.domain.ResourceScope;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskChecklistItem;
import pj.dooit.task.dto.TaskChecklistItemOrderRequest;
import pj.dooit.task.dto.TaskChecklistItemRequest;
import pj.dooit.task.dto.TaskChecklistItemResponse;
import pj.dooit.task.exception.TaskChecklistItemNotFoundException;
import pj.dooit.task.exception.TaskNotFoundException;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.task.repository.TaskChecklistItemRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.WorkspaceMember;
import pj.dooit.workspace.domain.WorkspaceMemberStatus;
import pj.dooit.workspace.domain.WorkspaceRole;
import pj.dooit.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskChecklistItemService {

    private static final int MAX_ITEMS_PER_TASK = 100;

    private final TaskChecklistItemRepository checklistItemRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<TaskChecklistItemResponse> findAllForOwner(Long taskId, User owner) {
        requireReadableTask(taskId, owner);
        return checklistItemRepository.findAllByTaskIdOrderBySortOrderAscIdAsc(taskId).stream()
                .map(TaskChecklistItemResponse::from)
                .toList();
    }

    @Transactional
    public TaskChecklistItemResponse createForOwner(Long taskId, TaskChecklistItemRequest request, User owner) {
        Task task = requireEditableTask(taskId, owner);
        if (checklistItemRepository.countByTaskId(taskId) >= MAX_ITEMS_PER_TASK) {
            throw new TaskValidationException("Task checklist item은 최대 100개까지 만들 수 있습니다.");
        }
        TaskChecklistItem item = new TaskChecklistItem(
                task,
                request.title(),
                checklistItemRepository.findMaxSortOrder(taskId) + 1
        );
        return TaskChecklistItemResponse.from(checklistItemRepository.save(item));
    }

    @Transactional
    public TaskChecklistItemResponse updateForOwner(Long taskId, Long itemId, TaskChecklistItemRequest request, User owner) {
        requireEditableTask(taskId, owner);
        TaskChecklistItem item = findItem(taskId, itemId);
        item.updateTitle(request.title());
        return TaskChecklistItemResponse.from(checklistItemRepository.save(item));
    }

    @Transactional
    public TaskChecklistItemResponse completeForOwner(Long taskId, Long itemId, LocalDateTime completedAt, User owner) {
        requireEditableTask(taskId, owner);
        TaskChecklistItem item = findItem(taskId, itemId);
        item.complete(completedAt);
        return TaskChecklistItemResponse.from(checklistItemRepository.save(item));
    }

    @Transactional
    public TaskChecklistItemResponse reopenForOwner(Long taskId, Long itemId, User owner) {
        requireEditableTask(taskId, owner);
        TaskChecklistItem item = findItem(taskId, itemId);
        item.reopen();
        return TaskChecklistItemResponse.from(checklistItemRepository.save(item));
    }

    @Transactional
    public void deleteForOwner(Long taskId, Long itemId, User owner) {
        requireEditableTask(taskId, owner);
        TaskChecklistItem item = findItem(taskId, itemId);
        checklistItemRepository.delete(item);
        normalizeSortOrder(taskId);
    }

    @Transactional
    public List<TaskChecklistItemResponse> reorderForOwner(Long taskId, TaskChecklistItemOrderRequest request, User owner) {
        requireEditableTask(taskId, owner);
        List<TaskChecklistItem> items = checklistItemRepository.findAllByTaskIdOrderBySortOrderAscIdAsc(taskId);
        List<Long> orderedItemIds = validateOrderRequest(request, items);
        Map<Long, TaskChecklistItem> itemById = items.stream()
                .collect(Collectors.toMap(TaskChecklistItem::getId, Function.identity()));
        List<TaskChecklistItem> reordered = orderedItemIds.stream()
                .map(itemById::get)
                .toList();
        for (int i = 0; i < reordered.size(); i++) {
            reordered.get(i).assignSortOrder(i);
        }
        return checklistItemRepository.saveAll(reordered).stream()
                .map(TaskChecklistItemResponse::from)
                .toList();
    }

    @Transactional
    public void completeAllForTask(Long taskId, LocalDateTime completedAt) {
        if (taskId == null) {
            return;
        }
        checklistItemRepository.findAllByTaskIdOrderBySortOrderAscIdAsc(taskId).stream()
                .filter(item -> !item.isDone())
                .forEach(item -> item.complete(completedAt));
    }

    @Transactional
    public void deleteAllForTask(Long taskId) {
        if (taskId != null) {
            checklistItemRepository.deleteAllByTaskId(taskId);
        }
    }

    private Task requireReadableTask(Long taskId, User actor) {
        Task task = taskRepository.findByIdAndOwnerId(taskId, ownerId(actor))
                .orElse(null);
        if (task != null) {
            return task;
        }
        task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        requireWorkspaceAccess(task, actor, false);
        return task;
    }

    private Task requireEditableTask(Long taskId, User actor) {
        Task task = taskRepository.findByIdAndOwnerId(taskId, ownerId(actor))
                .orElse(null);
        if (task != null) {
            return task;
        }
        task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        requireWorkspaceAccess(task, actor, true);
        return task;
    }

    private void requireWorkspaceAccess(Task task, User actor, boolean editable) {
        if (task.getScope() != ResourceScope.WORKSPACE || task.getWorkspace() == null) {
            throw new TaskNotFoundException(task.getId());
        }
        WorkspaceMember member = workspaceMemberRepository.findByWorkspaceIdAndUserId(
                        task.getWorkspace().getId(),
                        ownerId(actor)
                )
                .orElseThrow(() -> new TaskNotFoundException(task.getId()));
        if (member.getStatus() != WorkspaceMemberStatus.ACTIVE) {
            throw new TaskNotFoundException(task.getId());
        }
        if (editable && member.getRole() == WorkspaceRole.VIEWER) {
            throw new AccessDeniedException("workspace editor 권한이 필요합니다.");
        }
    }

    private TaskChecklistItem findItem(Long taskId, Long itemId) {
        return checklistItemRepository.findByIdAndTaskId(itemId, taskId)
                .orElseThrow(() -> new TaskChecklistItemNotFoundException(itemId));
    }

    private List<Long> validateOrderRequest(TaskChecklistItemOrderRequest request, List<TaskChecklistItem> items) {
        List<Long> orderedItemIds = request == null || request.orderedItemIds() == null
                ? List.of()
                : request.orderedItemIds();
        if (orderedItemIds.size() != items.size()) {
            throw new TaskValidationException("orderedItemIds는 현재 checklist item 전체를 포함해야 합니다.");
        }
        Set<Long> expected = items.stream().map(TaskChecklistItem::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (Long id : orderedItemIds) {
            if (id == null || !expected.contains(id) || !seen.add(id)) {
                throw new TaskValidationException("orderedItemIds는 현재 checklist item 전체를 중복 없이 포함해야 합니다.");
            }
        }
        return List.copyOf(orderedItemIds);
    }

    private void normalizeSortOrder(Long taskId) {
        List<TaskChecklistItem> items = checklistItemRepository.findAllByTaskIdOrderBySortOrderAscIdAsc(taskId);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).assignSortOrder(i);
        }
        checklistItemRepository.saveAll(items);
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
