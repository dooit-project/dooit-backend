package pj.dooit.goal.service;

import pj.dooit.Constant;
import pj.dooit.dailyplan.service.DailyPlanService;
import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalPlan;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPracticeResult;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.exception.GoalOccurrenceConflictException;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.task.service.TaskChecklistItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoalOccurrenceTaskLinkService {
    private final GoalPlanOccurrenceRepository occurrenceRepository;
    private final GoalPracticeResultRepository resultRepository;
    private final TaskRepository taskRepository;
    private final DailyPlanService dailyPlanService;
    private final TaskChecklistItemService checklistItemService;

    @Transactional
    public void lockLinkedOccurrence(Long taskId) {
        occurrenceRepository.findByLinkedTaskId(taskId);
    }

    @Transactional
    public void suspendFutureTasks(GoalPlan plan) {
        occurrenceRepository.findByPlanIdOrderByServiceDateAscIdAsc(plan.getId())
                .forEach(occurrence -> syncPendingTask(occurrence, false));
    }

    @Transactional
    public void syncPendingTask(GoalPlanOccurrence occurrence, boolean scheduled) {
        if (occurrence.getStatus() != GoalOccurrenceStatus.PENDING) {
            return;
        }
        Task task = linkedTask(occurrence);
        boolean future = !occurrence.getServiceDate().isBefore(LocalDate.now(ZoneId.of(occurrence.getTimeZone())));
        if (!future) {
            return;
        }
        if (!scheduled) {
            if (task != null) {
                task.moveToInbox();
                dailyPlanService.removeFocusTask(task.getId());
            }
            return;
        }
        String title = truncate(occurrence.getSnapshotNextAction() == null
                ? occurrence.getSnapshotMinimumActionText() : occurrence.getSnapshotNextAction(), 255);
        String description = truncate("기본: %s\n최소: %s".formatted(
                occurrence.getSnapshotStandardActionText(), occurrence.getSnapshotMinimumActionText()), 255);
        if (task == null) {
            // Date-only Today task. The occurrence is the sole notification candidate source.
            task = taskRepository.save(Task.builder().owner(occurrence.getOwner())
                    .title(title).description(description).type(TaskType.TODO).category("목표 계획")
                    .notificationEnabled(false).status(TaskStatus.TODAY)
                    .targetDate(occurrence.getServiceDate()).build());
            occurrence.linkTask(task.getId());
        } else {
            task.update(title, description, TaskType.TODO, null, null, false, "목표 계획", null, false, null);
            task.moveToToday(occurrence.getServiceDate());
        }
    }

    @Transactional
    public void completeLinkedTask(GoalPlanOccurrence occurrence, LocalDateTime practicedAt, String timeZone) {
        Task task = linkedTask(occurrence);
        if (task == null) {
            return;
        }
        LocalDateTime completedAt = practicedAt.atZone(ZoneId.of(timeZone))
                .withZoneSameInstant(Constant.ZONE).toLocalDateTime();
        task.complete(completedAt);
        dailyPlanService.removeFocusTask(task.getId());
        checklistItemService.completeAllForTask(task.getId(), task.getCompletedAt());
    }

    @Transactional
    public void resetLinkedTask(GoalPlanOccurrence occurrence, boolean rested) {
        Task task = linkedTask(occurrence);
        if (task == null) {
            return;
        }
        boolean future = !occurrence.getServiceDate().isBefore(LocalDate.now(ZoneId.of(occurrence.getTimeZone())));
        if (rested || (future && occurrence.getPlan().getStatus() != GoalPlanStatus.ACTIVE)) {
            task.moveToInbox();
            dailyPlanService.removeFocusTask(task.getId());
        } else {
            task.reopenToday(occurrence.getServiceDate());
        }
    }

    @Transactional
    public void recordLinkedTaskCompleted(Task task, LocalDateTime completedAt) {
        occurrenceRepository.findByLinkedTaskId(task.getId()).ifPresent(occurrence -> {
            if (occurrence.getStatus() == GoalOccurrenceStatus.RESTED) {
                throw new GoalOccurrenceConflictException("휴식 취소 후 Task를 완료할 수 있습니다.");
            }
            if (!resultRepository.existsById(occurrence.getId())) {
                LocalDateTime practicedAt = completedAt.atZone(Constant.ZONE)
                        .withZoneSameInstant(ZoneId.of(occurrence.getTimeZone())).toLocalDateTime();
                resultRepository.save(new GoalPracticeResult(occurrence, ActionVariant.UNKNOWN, practicedAt,
                        occurrence.getTimeZone(), null, List.of(), NextActionPatchKind.KEEP, null));
            } else {
                // Keep an explicit STANDARD/MINIMUM result and its original timestamp on repeated Task completion.
                GoalPracticeResult result = resultRepository.findById(occurrence.getId()).orElseThrow();
                task.complete(result.getPracticedAt().atZone(ZoneId.of(result.getPracticedTimeZone()))
                        .withZoneSameInstant(Constant.ZONE).toLocalDateTime());
            }
            occurrence.markPracticed();
        });
    }

    @Transactional
    public void recordLinkedTaskReopened(Task task) {
        occurrenceRepository.findByLinkedTaskId(task.getId()).ifPresent(occurrence -> {
            if (occurrence.getStatus() == GoalOccurrenceStatus.RESTED) {
                throw new GoalOccurrenceConflictException("휴식 취소 후 Task를 재개할 수 있습니다.");
            }
            if (occurrence.getStatus() == GoalOccurrenceStatus.PRACTICED) {
                resultRepository.deleteById(occurrence.getId());
                occurrence.markPending();
                if (occurrence.getPlan().getStatus() != GoalPlanStatus.ACTIVE) {
                    resetLinkedTask(occurrence, false);
                }
            }
        });
    }

    @Transactional
    public void validateTaskDeletion(Task task) {
        if (occurrenceRepository.findByLinkedTaskId(task.getId()).isPresent()) {
            throw new GoalOccurrenceConflictException("회차 연결 Task는 삭제 대신 회차 휴식 또는 계획 보관을 사용해주세요.");
        }
    }

    private Task linkedTask(GoalPlanOccurrence occurrence) {
        return occurrence.getLinkedTaskId() == null ? null : taskRepository
                .findByIdAndOwnerId(occurrence.getLinkedTaskId(), occurrence.getOwner().getId()).orElseThrow();
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
