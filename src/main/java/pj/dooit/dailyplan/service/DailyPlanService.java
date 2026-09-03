package pj.dooit.dailyplan.service;

import pj.dooit.dailyplan.domain.DailyPlan;
import pj.dooit.dailyplan.dto.DailyPlanRequest;
import pj.dooit.dailyplan.dto.DailyPlanResponse;
import pj.dooit.dailyplan.dto.DailyPlanSummaryResponse;
import pj.dooit.dailyplan.repository.DailyPlanRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public DailyPlanResponse getForOwner(LocalDate date, User owner) {
        Long ownerId = ownerId(owner);
        return dailyPlanRepository.findByOwnerIdAndDate(ownerId, date)
                .map(DailyPlanResponse::from)
                .orElseGet(() -> DailyPlanResponse.empty(date));
    }

    @Transactional(readOnly = true)
    public DailyPlanSummaryResponse getSummaryForOwner(LocalDate date, User owner) {
        Long ownerId = ownerId(owner);
        DailyPlan dailyPlan = dailyPlanRepository.findByOwnerIdAndDate(ownerId, date)
                .orElse(null);
        if (dailyPlan == null) {
            return DailyPlanSummaryResponse.empty(date);
        }

        List<Long> plannedFocusTaskIds = plannedFocusTaskIds(dailyPlan);
        Map<Long, Task> tasksById = taskRepository.findAllById(plannedFocusTaskIds)
                .stream()
                .filter(task -> task.getOwner() != null && ownerId.equals(task.getOwner().getId()))
                .collect(Collectors.toMap(Task::getId, Function.identity()));

        int completedCount = 0;
        int movedToOtherDateCount = 0;
        int movedToInboxCount = 0;
        int undecidedCount = 0;
        for (Long taskId : plannedFocusTaskIds) {
            Task task = tasksById.get(taskId);
            if (task == null) {
                undecidedCount++;
            } else if (task.getStatus() == TaskStatus.DONE) {
                completedCount++;
            } else if (task.getStatus() == TaskStatus.INBOX) {
                movedToInboxCount++;
            } else if (task.getStatus() == TaskStatus.TODAY && !date.equals(task.getPlannedDate())) {
                movedToOtherDateCount++;
            } else {
                undecidedCount++;
            }
        }

        return new DailyPlanSummaryResponse(
                date,
                dailyPlan.getStatus(),
                plannedFocusTaskIds.size(),
                completedCount,
                movedToOtherDateCount,
                movedToInboxCount,
                undecidedCount
        );
    }

    @Transactional
    public DailyPlanResponse replaceForOwner(LocalDate date, DailyPlanRequest request, User owner) {
        Long ownerId = ownerId(owner);
        List<Long> focusTaskIds = normalizedFocusTaskIds(request == null ? null : request.focusTaskIds());
        validateFocusTasks(focusTaskIds, date, ownerId);
        DailyPlan dailyPlan = dailyPlanRepository.findByOwnerIdAndDate(ownerId, date)
                .orElseGet(() -> new DailyPlan(owner, date));
        dailyPlan.replace(focusTaskIds, request == null ? null : request.status());
        return DailyPlanResponse.from(dailyPlanRepository.save(dailyPlan));
    }

    @Transactional
    public void removeFocusTask(Long taskId) {
        if (taskId != null) {
            dailyPlanRepository.deleteFocusTaskReferences(taskId);
        }
    }

    private List<Long> normalizedFocusTaskIds(List<Long> focusTaskIds) {
        if (focusTaskIds == null) {
            return List.of();
        }
        if (focusTaskIds.size() > 3) {
            throw new TaskValidationException("focusTaskIds는 최대 3개까지 저장할 수 있습니다.");
        }
        Set<Long> seen = new HashSet<>();
        for (Long taskId : focusTaskIds) {
            if (taskId == null) {
                throw new TaskValidationException("focusTaskIds에는 null을 포함할 수 없습니다.");
            }
            if (!seen.add(taskId)) {
                throw new TaskValidationException("focusTaskIds에는 중복 Task ID를 포함할 수 없습니다.");
            }
        }
        return List.copyOf(focusTaskIds);
    }

    private List<Long> plannedFocusTaskIds(DailyPlan dailyPlan) {
        if (!dailyPlan.getInitialFocusTaskIds().isEmpty()) {
            return List.copyOf(dailyPlan.getInitialFocusTaskIds());
        }
        return List.copyOf(dailyPlan.getFocusTaskIds());
    }

    private void validateFocusTasks(List<Long> focusTaskIds, LocalDate date, Long ownerId) {
        for (Long taskId : focusTaskIds) {
            Task task = taskRepository.findByIdAndOwnerId(taskId, ownerId)
                    .orElseThrow(() -> new TaskValidationException("focusTaskIds에는 본인 소유 개인 Task만 포함할 수 있습니다."));
            if (task.getStatus() != TaskStatus.TODAY || task.getCompletedAt() != null || !date.equals(task.getPlannedDate())) {
                throw new TaskValidationException("focusTaskIds에는 같은 날짜의 미완료 Today Task만 포함할 수 있습니다.");
            }
        }
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }
}
