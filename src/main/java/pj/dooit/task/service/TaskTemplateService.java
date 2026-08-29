package pj.dooit.task.service;

import pj.dooit.Constant;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.TaskTemplate;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskRecurrenceRequest;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.dto.TaskTemplateCreateTaskRequest;
import pj.dooit.task.dto.TaskTemplateRequest;
import pj.dooit.task.dto.TaskTemplateResponse;
import pj.dooit.task.exception.TaskTemplateNotFoundException;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.task.repository.TaskTemplateRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private final TaskTemplateRepository taskTemplateRepository;
    private final TaskService taskService;

    @Transactional
    public TaskTemplateResponse createForOwner(TaskTemplateRequest request, User owner) {
        TaskTemplate template = new TaskTemplate(
                owner,
                request.title(),
                request.description(),
                request.type(),
                request.category(),
                request.allDay(),
                request.defaultStartTime(),
                request.defaultDurationMinutes(),
                request.recurrenceFrequency(),
                request.recurrenceInterval(),
                joinByDays(request.recurrenceByDays())
        );
        return TaskTemplateResponse.from(taskTemplateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public List<TaskTemplateResponse> findAllForOwner(User owner) {
        return taskTemplateRepository.findAllByOwnerIdOrderByIdAsc(ownerId(owner)).stream()
                .map(TaskTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskTemplateResponse getForOwner(Long id, User owner) {
        return TaskTemplateResponse.from(findForOwner(id, owner));
    }

    @Transactional
    public TaskTemplateResponse updateForOwner(Long id, TaskTemplateRequest request, User owner) {
        TaskTemplate template = findForOwner(id, owner);
        template.update(
                request.title(),
                request.description(),
                request.type(),
                request.category(),
                request.allDay(),
                request.defaultStartTime(),
                request.defaultDurationMinutes(),
                request.recurrenceFrequency(),
                request.recurrenceInterval(),
                joinByDays(request.recurrenceByDays())
        );
        return TaskTemplateResponse.from(template);
    }

    @Transactional
    public void deleteForOwner(Long id, User owner) {
        taskTemplateRepository.delete(findForOwner(id, owner));
    }

    @Transactional
    public TaskResponse createTaskForOwner(Long id, TaskTemplateCreateTaskRequest request, User owner) {
        TaskTemplate template = findForOwner(id, owner);
        TaskRequest taskRequest = toTaskRequest(template, request);
        TaskResponse created = taskService.createForOwner(taskRequest, owner);
        if (request.ddayGoalId() == null) {
            return created;
        }
        return taskService.connectDdayGoalForOwner(created.id(), request.ddayGoalId(), owner);
    }

    private TaskTemplate findForOwner(Long id, User owner) {
        return taskTemplateRepository.findByIdAndOwnerId(id, ownerId(owner))
                .orElseThrow(() -> new TaskTemplateNotFoundException(id));
    }

    private TaskRequest toTaskRequest(TaskTemplate template, TaskTemplateCreateTaskRequest request) {
        String title = overrideOrDefault(request.title(), template.getTitle());
        String description = overrideOrDefault(request.description(), template.getDescription());
        String category = overrideOrDefault(request.category(), template.getCategory());
        LocalDate targetDate = request.targetDate();
        LocalDateTime startAt = null;
        LocalDateTime endAt = null;
        boolean allDay = false;

        if (template.getType() == TaskType.SCHEDULE || template.getRecurrenceFrequency() != null) {
            if (targetDate == null) {
                throw new TaskValidationException("일정 또는 반복 템플릿으로 Task를 만들려면 targetDate가 필요합니다.");
            }
            if (!template.isAllDay() && template.getDefaultStartTime() == null) {
                throw new TaskValidationException("일정 템플릿에는 allDay 또는 defaultStartTime이 필요합니다.");
            }
        }

        if (targetDate != null && template.isAllDay()) {
            startAt = targetDate.atStartOfDay();
            endAt = targetDate.plusDays(1).atStartOfDay();
            allDay = true;
        } else if (targetDate != null && template.getDefaultStartTime() != null) {
            LocalTime startTime = template.getDefaultStartTime();
            startAt = targetDate.atTime(startTime);
            endAt = startAt.plusMinutes(durationMinutes(template));
        }

        TaskRecurrenceRequest recurrence = recurrenceRequest(template, startAt);
        return new TaskRequest(
                title,
                description,
                template.getType(),
                startAt,
                endAt,
                category,
                allDay,
                recurrence
        );
    }

    private TaskRecurrenceRequest recurrenceRequest(TaskTemplate template, LocalDateTime startAt) {
        RecurrenceFrequency frequency = template.getRecurrenceFrequency();
        if (frequency == null) {
            return null;
        }
        if (startAt == null) {
            throw new TaskValidationException("반복 템플릿으로 Task를 만들려면 시작 일시가 필요합니다.");
        }

        return new TaskRecurrenceRequest(
                frequency,
                template.getRecurrenceInterval(),
                null,
                Constant.ZONE_ID,
                null,
                null,
                splitByDays(template.getRecurrenceByDays()),
                null
        );
    }

    private int durationMinutes(TaskTemplate template) {
        return template.getDefaultDurationMinutes() == null
                ? DEFAULT_DURATION_MINUTES
                : template.getDefaultDurationMinutes();
    }

    private String overrideOrDefault(String override, String defaultValue) {
        if (override == null || override.isBlank()) {
            return defaultValue;
        }
        return override.trim();
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }

    private String joinByDays(List<String> byDays) {
        List<String> normalized = normalizeByDays(byDays);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private List<String> splitByDays(String byDays) {
        if (byDays == null || byDays.isBlank()) {
            return List.of();
        }
        return normalizeByDays(Arrays.asList(byDays.split(",")));
    }

    private List<String> normalizeByDays(List<String> byDays) {
        if (byDays == null) {
            return List.of();
        }
        return byDays.stream()
                .map(day -> day == null ? "" : day.trim().toUpperCase(Locale.ROOT))
                .filter(day -> !day.isBlank())
                .peek(this::validateByDay)
                .toList();
    }

    private void validateByDay(String day) {
        if (!List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU").contains(day)) {
            throw new TaskValidationException("올바르지 않은 반복 요일입니다.");
        }
    }
}
