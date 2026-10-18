package pj.dooit.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pj.dooit.goal.service.GoalTaskLinkService;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskQuickCaptureRequest;
import pj.dooit.task.dto.TaskQuickCaptureResponse;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.workspace.domain.SharedWorkspace;

import java.time.LocalDate;

/** 일반 생성과 빠른 기록의 검증, 목표 연결, 반복 시리즈 저장을 하나의 트랜잭션에서 처리한다. */
@Service
@RequiredArgsConstructor
public class TaskCreationService {

    private final TaskRepository taskRepository;
    private final RecurrenceSeriesRepository recurrenceSeriesRepository;
    private final GoalTaskLinkService goalTaskLinkService;
    private final TaskQuickCaptureParser taskQuickCaptureParser;

    @Transactional
    public TaskResponse create(TaskRequest req) {
        return create(req, null);
    }

    @Transactional
    public TaskResponse createForOwner(TaskRequest req, User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        return create(req, owner, null);
    }

    @Transactional
    public TaskResponse createForWorkspace(TaskRequest req, User actor, SharedWorkspace workspace) {
        if (actor == null) {
            throw new IllegalArgumentException("actor는 필수입니다.");
        }
        if (workspace == null || workspace.getId() == null) {
            throw new IllegalArgumentException("workspace는 영속화된 workspace여야 합니다.");
        }
        return create(req, actor, workspace);
    }

    @Transactional
    public TaskQuickCaptureResponse quickCaptureForOwner(TaskQuickCaptureRequest request, User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }

        TaskQuickCaptureParser.ParsedQuickCapture parsed = taskQuickCaptureParser.parse(request, owner);
        TaskResponse task = create(parsed.taskRequest(), owner, null,
                parsed.parsedType() == TaskType.TODO ? parsed.parsedDate() : null);
        return new TaskQuickCaptureResponse(
                task,
                parsed.parsed(),
                parsed.originalText(),
                parsed.parsedDate(),
                parsed.parsedTime(),
                parsed.parsedType(),
                parsed.parsedRecurrenceFrequency(),
                parsed.parsedByDays(),
                parsed.timeZone()
        );
    }

    private TaskResponse create(TaskRequest req, User owner) {
        return create(req, owner, null);
    }

    private TaskResponse create(TaskRequest req, User owner, SharedWorkspace workspace) {
        return create(req, owner, workspace, null);
    }

    private TaskResponse create(TaskRequest req, User owner, SharedWorkspace workspace, LocalDate plannedDate) {
        req.validate();
        Task task = Task.builder()
                .title(req.title())
                .description(req.description())
                .type(req.normalizedType())
                .startAt(req.startAt())
                .endAt(req.endAt())
                .allDay(req.allDay())
                .category(req.category())
                .estimatedDurationMinutes(req.estimatedDurationMinutes())
                .notificationEnabled(req.notificationEnabled())
                .notifyAt(req.notifyAt())
                .owner(owner)
                .build();
        if (workspace != null) {
            task.assignWorkspace(workspace);
        }

        if (req.linkedGoalId() != null) {
            goalTaskLinkService.applyRequest(task, req);
        }

        if (req.recurrence() != null) {
            RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                    owner,
                    req.recurrence().normalizedFrequency(),
                    req.recurrence().normalizedInterval(),
                    req.recurrence().normalizedRecurrenceRule(),
                    req.recurrence().normalizedTimeZone(),
                    req.startAt(),
                    req.recurrence().recurrenceUntil(),
                    req.recurrence().recurrenceCount()
            ));
            if (workspace != null) {
                series.assignWorkspace(workspace);
            }
            task.connectRecurrenceSeries(series, req.startAt().toLocalDate());
        }

        if (plannedDate != null) {
            task.planTodo(plannedDate);
        }
        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

}
