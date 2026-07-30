package com.todolab.task.service;

import com.todolab.task.domain.DeferReason;
import com.todolab.task.domain.RecurrenceEditScope;
import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TodayOrderDirection;
import com.todolab.task.domain.query.DateRange;
import com.todolab.task.domain.query.TaskQueryType;
import com.todolab.task.domain.query.TaskSearchDateField;
import com.todolab.task.domain.query.TaskSearchDateSource;
import com.todolab.task.domain.query.TaskSearchSort;
import com.todolab.task.dto.TaskCategoryGroupResponse;
import com.todolab.task.dto.TaskNotificationCandidateResponse;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskQueryRequest;
import com.todolab.task.dto.TaskRecommendationResponse;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.dto.TaskSearchItemResponse;
import com.todolab.task.dto.TaskSearchRequest;
import com.todolab.task.dto.TaskSearchResponse;
import com.todolab.task.dto.TodayOrderRequest;
import com.todolab.task.exception.TaskNotFoundException;
import com.todolab.task.exception.TaskValidationException;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskTxService taskTxService;
    private final TaskRepository taskRepository;
    private final RecurrenceSeriesRepository recurrenceSeriesRepository;
    private final TaskCategoryGrouper taskCategoryGrouper;
    private final RecurrenceOccurrenceMaterializer recurrenceOccurrenceMaterializer;

    @Transactional
    public TaskResponse create(TaskRequest req) {
        return create(req, null);
    }

    @Transactional
    public TaskResponse createForOwner(TaskRequest req, User owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner는 필수입니다.");
        }
        return create(req, owner);
    }

    private TaskResponse create(TaskRequest req, User owner) {
        req.validate();
        Task task = Task.builder()
                .title(req.title())
                .description(req.description())
                .type(req.normalizedType())
                .startAt(req.startAt())
                .endAt(req.endAt())
                .allDay(req.allDay())
                .category(req.category())
                .owner(owner)
                .build();

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
            task.connectRecurrenceSeries(series, req.startAt().toLocalDate());
        }

        Task saved = taskRepository.save(task);
        return TaskResponse.from(saved);
    }

    public TaskResponse getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        return TaskResponse.from(task);
    }

    public TaskResponse getTaskForOwner(Long id, User owner) {
        Task task = taskRepository.findByIdAndOwnerId(id, ownerId(owner))
                .orElseThrow(() -> new TaskNotFoundException(id));

        return TaskResponse.from(task);
    }

    public List<TaskResponse> getTasks(TaskQueryRequest request) {
        return findTasks(request);
    }

    public List<TaskResponse> getTasksForOwner(TaskQueryRequest request, User owner) {
        return findTasks(request, ownerId(owner), owner);
    }

    public List<TaskCategoryGroupResponse> getGroupedTasks(TaskQueryRequest request) {
        return taskCategoryGrouper.group(findTasks(request));
    }

    public List<TaskResponse> getUnscheduledTasks() {
        return findUnscheduledTasks();
    }

    public List<TaskResponse> getUnscheduledTasksForOwner(User owner) {
        return findUnscheduledTasks(ownerId(owner));
    }

    public List<TaskCategoryGroupResponse> getGroupedUnscheduledTasks() {
        return taskCategoryGrouper.group(findUnscheduledTasks());
    }

    public TaskSearchResponse searchTasksForOwner(TaskSearchRequest request, User owner) {
        List<SearchCandidate> candidates = taskRepository.findByOwnerId(ownerId(owner)).stream()
                .filter(task -> matchesText(task, request.getQ()))
                .filter(task -> request.getStatuses().isEmpty() || request.getStatuses().contains(task.getStatus()))
                .filter(task -> request.getTaskTypes().isEmpty() || request.getTaskTypes().contains(task.getType()))
                .filter(task -> request.getCategory() == null || request.getCategory().equals(task.getCategory()))
                .filter(task -> request.getDdayGoalId() == null
                        || (task.getDdayGoal() != null && request.getDdayGoalId().equals(task.getDdayGoal().getId())))
                .filter(task -> request.getHasDday() == null || request.getHasDday().equals(task.getDdayGoal() != null))
                .filter(task -> request.getAllDay() == null || request.getAllDay().equals(task.isAllDay()))
                .map(task -> SearchCandidate.from(task, request.getDateField()))
                .filter(candidate -> matchesDateRange(candidate.relevantDate(), request))
                .sorted(searchComparator(request.getSort()))
                .toList();

        int fromIndex = cursorFromIndex(candidates, request.getCursorTaskId());
        int toIndex = Math.min(fromIndex + request.getLimit(), candidates.size());
        String nextCursor = toIndex < candidates.size()
                ? String.valueOf(candidates.get(toIndex - 1).task().getId())
                : null;
        List<TaskSearchItemResponse> items = candidates.subList(fromIndex, toIndex).stream()
                .map(SearchCandidate::toResponse)
                .toList();

        return new TaskSearchResponse(items, nextCursor, request.getLimit());
    }

    private int cursorFromIndex(List<SearchCandidate> candidates, Long cursorTaskId) {
        if (cursorTaskId == null) {
            return 0;
        }

        for (int i = 0; i < candidates.size(); i++) {
            if (cursorTaskId.equals(candidates.get(i).task().getId())) {
                return i + 1;
            }
        }

        throw new TaskValidationException("올바르지 않은 cursor 값입니다.");
    }

    public List<TaskResponse> getInboxTasks() {
        return taskRepository.findByStatus(TaskStatus.INBOX).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getInboxTasksForOwner(User owner) {
        return taskRepository.findByStatus(ownerId(owner), TaskStatus.INBOX).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskRecommendationResponse> getTodayRecommendations(LocalDate referenceDate) {
        List<TaskResponse> overdueTasks = taskRepository.findPlannedTasks(null, referenceDate).stream()
                .map(TaskResponse::from)
                .toList();
        List<TaskResponse> inboxTasks = taskRepository.findByStatus(TaskStatus.INBOX).stream()
                .map(TaskResponse::from)
                .toList();

        return java.util.stream.Stream.concat(overdueTasks.stream(), inboxTasks.stream())
                .map(task -> RecommendationCandidate.from(task, referenceDate))
                .sorted(Comparator
                        .comparingInt(RecommendationCandidate::priority)
                        .thenComparingLong(RecommendationCandidate::sortKey)
                        .thenComparing(candidate -> candidate.task().id(), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(candidate -> new TaskRecommendationResponse(candidate.task(), candidate.reason()))
                .toList();
    }

    public List<TaskRecommendationResponse> getTodayRecommendationsForOwner(LocalDate referenceDate, User owner) {
        Long ownerId = ownerId(owner);
        List<TaskResponse> overdueTasks = taskRepository.findPlannedTasks(ownerId, null, referenceDate).stream()
                .map(TaskResponse::from)
                .toList();
        List<TaskResponse> inboxTasks = taskRepository.findByStatus(ownerId, TaskStatus.INBOX).stream()
                .map(TaskResponse::from)
                .toList();

        return java.util.stream.Stream.concat(overdueTasks.stream(), inboxTasks.stream())
                .map(task -> RecommendationCandidate.from(task, referenceDate))
                .sorted(Comparator
                        .comparingInt(RecommendationCandidate::priority)
                        .thenComparingLong(RecommendationCandidate::sortKey)
                        .thenComparing(candidate -> candidate.task().id(), Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(candidate -> new TaskRecommendationResponse(candidate.task(), candidate.reason()))
                .toList();
    }

    public List<TaskNotificationCandidateResponse> getNotificationCandidatesForOwner(
            LocalDate fromInclusive,
            LocalDate toInclusive,
            User owner
    ) {
        validateNotificationCandidateRange(fromInclusive, toInclusive);
        Long ownerId = ownerId(owner);
        DateRange serviceRange = DateRange.of(fromInclusive.atStartOfDay(), toInclusive.plusDays(1).atStartOfDay())
                .toServiceZone(ZoneId.of(owner.getTimeZone()));
        recurrenceOccurrenceMaterializer.materializeForOwner(
                ownerId,
                serviceRange.materializeFromInclusive(),
                serviceRange.materializeToExclusive()
        );
        return taskRepository.findNotificationCandidateTasks(ownerId, serviceRange.getStart(), serviceRange.getEnd()).stream()
                .filter(task -> task.getStartAt() != null)
                .filter(task -> task.getCompletedAt() == null)
                .filter(task -> task.getRecurrenceException() != com.todolab.task.domain.RecurrenceExceptionType.SKIPPED)
                .sorted(Comparator.comparing(Task::getStartAt).thenComparing(Task::getId))
                .map(TaskNotificationCandidateResponse::from)
                .toList();
    }

    public List<TaskResponse> getTodayTasks(LocalDate targetDate) {
        return taskRepository.findTodayTasks(targetDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getTodayTasksForOwner(LocalDate targetDate, User owner) {
        Long ownerId = ownerId(owner);
        DateRange serviceRange = DateRange.ofDay(targetDate.toString()).toServiceZone(ZoneId.of(owner.getTimeZone()));
        recurrenceOccurrenceMaterializer.materializeForOwner(
                ownerId,
                serviceRange.materializeFromInclusive(),
                serviceRange.materializeToExclusive()
        );
        return taskRepository.findTodayTasks(ownerId, targetDate, serviceRange.getStart(), serviceRange.getEnd()).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getPlannedTasksBetween(LocalDate startDate, LocalDate endDate) {
        return taskRepository.findPlannedTasks(startDate, endDate.plusDays(1)).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getPlannedTasksBetweenForOwner(LocalDate startDate, LocalDate endDate, User owner) {
        Long ownerId = ownerId(owner);
        recurrenceOccurrenceMaterializer.materializeForOwner(ownerId, startDate, endDate.plusDays(1));
        return taskRepository.findPlannedTasks(ownerId, startDate, endDate.plusDays(1)).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getOverdueTasks(LocalDate beforeDate) {
        return taskRepository.findPlannedTasks(null, beforeDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getOverdueTasksForOwner(LocalDate beforeDate, User owner) {
        return taskRepository.findPlannedTasks(ownerId(owner), null, beforeDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getDoneTasks(LocalDate completedDate) {
        return taskRepository.findDoneTasks(completedDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getDoneTasksForOwner(LocalDate completedDate, User owner) {
        return taskRepository.findDoneTasks(ownerId(owner), completedDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getDoneTasksBetween(LocalDate startDate, LocalDate endDate) {
        return taskRepository.findDoneTasksBetween(startDate, endDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public List<TaskResponse> getDoneTasksBetweenForOwner(LocalDate startDate, LocalDate endDate, User owner) {
        return taskRepository.findDoneTasksBetween(ownerId(owner), startDate, endDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse update(Long id, TaskRequest taskRequest) {
        Task updated = taskTxService.updateTx(id, taskRequest);
        return TaskResponse.from(updated);
    }

    public TaskResponse updateForOwner(Long id, TaskRequest taskRequest, User owner) {
        return updateForOwner(id, taskRequest, owner, RecurrenceEditScope.THIS);
    }

    public TaskResponse updateForOwner(Long id, TaskRequest taskRequest, User owner, RecurrenceEditScope recurrenceScope) {
        Task updated = taskTxService.updateTxForOwner(id, taskRequest, owner, recurrenceScope);
        return TaskResponse.from(updated);
    }

    public TaskResponse moveToToday(Long id, LocalDate targetDate) {
        Task moved = taskTxService.moveToTodayTx(id, targetDate);
        return TaskResponse.from(moved);
    }

    public TaskResponse moveToTodayForOwner(Long id, LocalDate targetDate, User owner) {
        Task moved = taskTxService.moveToTodayTxForOwner(id, targetDate, owner);
        return TaskResponse.from(moved);
    }

    public TaskResponse moveToInbox(Long id) {
        Task moved = taskTxService.moveToInboxTx(id);
        return TaskResponse.from(moved);
    }

    public TaskResponse moveToInboxForOwner(Long id, User owner) {
        Task moved = taskTxService.moveToInboxTxForOwner(id, owner);
        return TaskResponse.from(moved);
    }

    public TaskResponse complete(Long id, LocalDateTime completedAt) {
        Task completed = taskTxService.completeTx(id, completedAt);
        return TaskResponse.from(completed);
    }

    public TaskResponse completeForOwner(Long id, LocalDateTime completedAt, User owner) {
        Task completed = taskTxService.completeTxForOwner(id, completedAt, owner);
        return TaskResponse.from(completed);
    }

    public TaskResponse reopenToday(Long id, LocalDate targetDate) {
        Task reopened = taskTxService.reopenTodayTx(id, targetDate);
        return TaskResponse.from(reopened);
    }

    public TaskResponse reopenTodayForOwner(Long id, LocalDate targetDate, User owner) {
        Task reopened = taskTxService.reopenTodayTxForOwner(id, targetDate, owner);
        return TaskResponse.from(reopened);
    }

    public TaskResponse carryOver(Long id, LocalDate nextDate) {
        Task carriedOver = taskTxService.carryOverTx(id, nextDate);
        return TaskResponse.from(carriedOver);
    }

    public TaskResponse carryOverForOwner(Long id, LocalDate nextDate, User owner) {
        Task carriedOver = taskTxService.carryOverTxForOwner(id, nextDate, owner);
        return TaskResponse.from(carriedOver);
    }

    public TaskResponse reorderToday(Long id, LocalDate targetDate, TodayOrderDirection direction) {
        Task reordered = taskTxService.reorderTodayTx(id, targetDate, direction);
        return TaskResponse.from(reordered);
    }

    public TaskResponse reorderTodayForOwner(Long id, LocalDate targetDate, TodayOrderDirection direction, User owner) {
        Task reordered = taskTxService.reorderTodayTxForOwner(id, targetDate, direction, owner);
        return TaskResponse.from(reordered);
    }

    public List<TaskResponse> reorderTodayForOwner(TodayOrderRequest request, User owner) {
        return taskTxService.reorderTodayTxForOwner(request, owner).stream()
                .map(TaskResponse::from)
                .toList();
    }

    public TaskResponse setDeferReason(Long id, DeferReason reason) {
        Task updated = taskTxService.setDeferReasonTx(id, reason);
        return TaskResponse.from(updated);
    }

    public TaskResponse setDeferReasonForOwner(Long id, DeferReason reason, User owner) {
        Task updated = taskTxService.setDeferReasonTxForOwner(id, reason, owner);
        return TaskResponse.from(updated);
    }

    public TaskResponse clearDeferReason(Long id) {
        Task updated = taskTxService.clearDeferReasonTx(id);
        return TaskResponse.from(updated);
    }

    public TaskResponse clearDeferReasonForOwner(Long id, User owner) {
        Task updated = taskTxService.clearDeferReasonTxForOwner(id, owner);
        return TaskResponse.from(updated);
    }

    public TaskResponse connectDdayGoal(Long id, Long ddayGoalId) {
        Task connected = taskTxService.connectDdayGoalTx(id, ddayGoalId);
        return TaskResponse.from(connected);
    }

    public TaskResponse connectDdayGoalForOwner(Long id, Long ddayGoalId, User owner) {
        Task connected = taskTxService.connectDdayGoalTxForOwner(id, ddayGoalId, owner);
        return TaskResponse.from(connected);
    }

    public TaskResponse disconnectDdayGoal(Long id) {
        Task disconnected = taskTxService.disconnectDdayGoalTx(id);
        return TaskResponse.from(disconnected);
    }

    public TaskResponse disconnectDdayGoalForOwner(Long id, User owner) {
        Task disconnected = taskTxService.disconnectDdayGoalTxForOwner(id, owner);
        return TaskResponse.from(disconnected);
    }

    public TaskResponse createTodayTaskForDdayGoalForOwner(Long ddayGoalId, String title, LocalDate targetDate, User owner) {
        Task created = taskTxService.createTodayTaskForDdayGoalTxForOwner(ddayGoalId, title, targetDate, owner);
        return TaskResponse.from(created);
    }

    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        taskRepository.deleteById(id);
    }

    public void deleteForOwner(Long id, User owner) {
        deleteForOwner(id, owner, RecurrenceEditScope.THIS);
    }

    public void deleteForOwner(Long id, User owner, RecurrenceEditScope recurrenceScope) {
        taskTxService.deleteTxForOwner(id, owner, recurrenceScope);
    }

    private List<TaskResponse> findTasks(TaskQueryRequest request) {
        return findTasks(request, null);
    }

    private List<TaskResponse> findTasks(TaskQueryRequest request, Long ownerId) {
        return findTasks(request, ownerId, null);
    }

    private List<TaskResponse> findTasks(TaskQueryRequest request, Long ownerId, User owner) {
        final TaskQueryType type = request.getType();
        final String strDate = request.getDate();

        DateRange range = type.calculate(strDate);
        DateRange serviceRange = owner == null ? range : range.toServiceZone(ZoneId.of(owner.getTimeZone()));
        if (ownerId != null) {
            recurrenceOccurrenceMaterializer.materializeForOwner(
                    ownerId,
                    serviceRange.materializeFromInclusive(),
                    serviceRange.materializeToExclusive()
            );
        }

        List<Task> tasks = ownerId == null
                ? taskRepository.findByDateRangeAndType(range.getStart(), range.getEnd(), request.getTaskType())
                : taskRepository.findByDateRangeAndType(ownerId, serviceRange.getStart(), serviceRange.getEnd(), request.getTaskType());

        return tasks
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    private List<TaskResponse> findUnscheduledTasks() {
        return findUnscheduledTasks(null);
    }

    private List<TaskResponse> findUnscheduledTasks(Long ownerId) {
        List<Task> tasks = ownerId == null
                ? taskRepository.findUnscheduledTask()
                : taskRepository.findUnscheduledTask(ownerId);

        return tasks.stream()
                .map(TaskResponse::from)
                .toList();
    }

    private Long ownerId(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("owner는 영속화된 사용자여야 합니다.");
        }
        return owner.getId();
    }

    private boolean matchesText(Task task, String q) {
        if (q == null) {
            return true;
        }

        String needle = q.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(task.getTitle(), needle) || containsIgnoreCase(task.getDescription(), needle);
    }

    private boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private boolean matchesDateRange(LocalDate relevantDate, TaskSearchRequest request) {
        if (!request.hasDateRange()) {
            return true;
        }
        if (relevantDate == null) {
            return false;
        }
        if (request.getDateFrom() != null && relevantDate.isBefore(request.getDateFrom())) {
            return false;
        }
        return request.getDateTo() == null || !relevantDate.isAfter(request.getDateTo());
    }

    private Comparator<SearchCandidate> searchComparator(TaskSearchSort sort) {
        Comparator<SearchCandidate> idAsc = Comparator.comparing(candidate -> candidate.task().getId(), Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sort) {
            case RELEVANT_DATE_DESC -> Comparator
                    .comparing(SearchCandidate::relevantDate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(idAsc);
            case CREATED_AT_ASC -> Comparator
                    .comparing((SearchCandidate candidate) -> candidate.task().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(idAsc);
            case CREATED_AT_DESC -> Comparator
                    .comparing((SearchCandidate candidate) -> candidate.task().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(idAsc);
            case UPDATED_AT_ASC -> Comparator
                    .comparing((SearchCandidate candidate) -> candidate.task().getUpdatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(idAsc);
            case UPDATED_AT_DESC -> Comparator
                    .comparing((SearchCandidate candidate) -> candidate.task().getUpdatedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(idAsc);
            case RELEVANT_DATE_ASC -> Comparator
                    .comparing(SearchCandidate::relevantDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(idAsc);
        };
    }

    private void validateNotificationCandidateRange(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null) {
            throw new TaskValidationException("알림 후보 조회 날짜 범위가 필요합니다.");
        }
        if (toInclusive.isBefore(fromInclusive)) {
            throw new TaskValidationException("알림 후보 조회 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(fromInclusive, toInclusive) > 30) {
            throw new TaskValidationException("알림 후보 조회 범위는 최대 31일입니다.");
        }
    }

    private record RecommendationCandidate(TaskResponse task, String reason, int priority, long sortKey) {
        static RecommendationCandidate from(TaskResponse task, LocalDate referenceDate) {
            if (task.carryOverCount() >= 3) {
                return new RecommendationCandidate(task, "다시 정리 필요", 0, -task.carryOverCount());
            }

            LocalDate plannedDate = task.plannedDate();
            if (task.status() == TaskStatus.TODAY && plannedDate != null && plannedDate.isBefore(referenceDate)) {
                long overdueDays = ChronoUnit.DAYS.between(plannedDate, referenceDate);
                return new RecommendationCandidate(task, "지난 미완료", 1, -overdueDays);
            }

            LocalDate ddayDate = task.ddayGoalTargetDate();
            if (ddayDate != null && !ddayDate.isBefore(referenceDate)) {
                long daysLeft = ChronoUnit.DAYS.between(referenceDate, ddayDate);
                if (!ddayDate.isAfter(referenceDate.plusDays(3))) {
                    return new RecommendationCandidate(task, "D-Day 3일 이내", 2, daysLeft);
                }
                if (!ddayDate.isAfter(referenceDate.plusDays(14))) {
                    return new RecommendationCandidate(task, "D-Day 임박", 3, daysLeft);
                }
            }

            LocalDateTime createdAt = task.createdAt();
            if (createdAt != null && !createdAt.toLocalDate().isAfter(referenceDate.minusDays(7))) {
                return new RecommendationCandidate(task, "오래 기록", 4, createdAtSortKey(createdAt));
            }

            return new RecommendationCandidate(
                    task,
                    "최근 기록",
                    5,
                    createdAt == null ? Long.MAX_VALUE : -createdAtSortKey(createdAt)
            );
        }

        private static long createdAtSortKey(LocalDateTime createdAt) {
            return createdAt.toLocalDate().toEpochDay() * 86_400L + createdAt.toLocalTime().toSecondOfDay();
        }
    }

    private record SearchCandidate(Task task, LocalDate relevantDate, TaskSearchDateSource dateSource) {

        static SearchCandidate from(Task task, TaskSearchDateField dateField) {
            return switch (dateField) {
                case PLANNED -> planned(task);
                case START -> task.getStartAt() == null
                        ? none(task)
                        : new SearchCandidate(task, task.getStartAt().toLocalDate(), TaskSearchDateSource.START_AT);
                case TARGET -> task.getTargetDate() == null
                        ? none(task)
                        : new SearchCandidate(task, task.getTargetDate(), TaskSearchDateSource.TARGET_DATE);
                case COMPLETED -> task.getCompletedAt() == null
                        ? none(task)
                        : new SearchCandidate(task, task.getCompletedAt().toLocalDate(), TaskSearchDateSource.COMPLETED_AT);
                case CREATED -> task.getCreatedAt() == null
                        ? none(task)
                        : new SearchCandidate(task, task.getCreatedAt().toLocalDate(), TaskSearchDateSource.CREATED_AT);
                case UPDATED -> task.getUpdatedAt() == null
                        ? none(task)
                        : new SearchCandidate(task, task.getUpdatedAt().toLocalDate(), TaskSearchDateSource.UPDATED_AT);
            };
        }

        private static SearchCandidate planned(Task task) {
            if (task.getTargetDate() != null) {
                return new SearchCandidate(task, task.getTargetDate(), TaskSearchDateSource.TARGET_DATE);
            }
            if (task.getStartAt() != null) {
                return new SearchCandidate(task, task.getStartAt().toLocalDate(), TaskSearchDateSource.START_AT);
            }
            return none(task);
        }

        private static SearchCandidate none(Task task) {
            return new SearchCandidate(task, null, TaskSearchDateSource.NONE);
        }

        TaskSearchItemResponse toResponse() {
            return new TaskSearchItemResponse(TaskResponse.from(task), relevantDate, dateSource);
        }
    }

}
