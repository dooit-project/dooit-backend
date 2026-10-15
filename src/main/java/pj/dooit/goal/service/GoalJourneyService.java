package pj.dooit.goal.service;

import pj.dooit.Constant;
import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.Goal;
import pj.dooit.goal.domain.GoalDirectLog;
import pj.dooit.goal.domain.GoalJourneySourceType;
import pj.dooit.goal.domain.GoalOccurrenceStatus;
import pj.dooit.goal.domain.GoalPlanOccurrence;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.GoalPlanVersion;
import pj.dooit.goal.domain.GoalPracticeResult;
import pj.dooit.goal.dto.GoalJourneyItem;
import pj.dooit.goal.dto.GoalJourneyRange;
import pj.dooit.goal.dto.GoalJourneyResponse;
import pj.dooit.goal.dto.GoalJourneySummary;
import pj.dooit.goal.dto.GoalResponse;
import pj.dooit.goal.exception.GoalJourneyConflictException;
import pj.dooit.goal.exception.GoalNotFoundException;
import pj.dooit.goal.exception.GoalValidationException;
import pj.dooit.goal.repository.GoalDirectLogRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPlanVersionRepository;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalVersionRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoalJourneyService {
    private static final Comparator<GoalJourneyItem> ORDER = Comparator
            .comparing(GoalJourneyItem::practicedDate, Comparator.reverseOrder())
            .thenComparing(item -> item.sourceType().name())
            .thenComparing(GoalJourneyItem::sourceId, Comparator.reverseOrder());

    private final GoalRepository goalRepository;
    private final GoalVersionRepository goalVersionRepository;
    private final GoalPlanOccurrenceRepository occurrenceRepository;
    private final GoalPlanVersionRepository planVersionRepository;
    private final GoalPracticeResultRepository resultRepository;
    private final GoalDirectLogRepository logRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GoalJourneyResponse get(Long goalId, LocalDate from, LocalDate to, String timeZone,
                                   String cursorValue, int limit, User owner) {
        ZoneId zone = validate(from, to, timeZone, limit);
        Goal goal = goalRepository.findByIdAndOwnerId(goalId, owner.getId())
                .orElseThrow(() -> new GoalNotFoundException("goalId=" + goalId));
        GoalJourneyRange range = new GoalJourneyRange(from, to, zone.getId());
        String scope = hash(objectMapper.writeValueAsBytes(List.of(owner.getId(), goalId, range)));
        Cursor cursor = decodeCursor(cursorValue);
        if (cursor != null && !scope.equals(cursor.scope())) {
            throw new GoalValidationException("조회 범위가 다른 cursor입니다.");
        }

        // A result's local timestamp can differ by up to two dates from the requested timezone.
        var results = resultRepository.findJourneyResults(owner.getId(), goalId,
                from.minusDays(2).atStartOfDay(), to.plusDays(3).atStartOfDay());
        var tasks = taskRepository.findGoalJourneyCompletions(owner.getId(), goalId,
                from.atStartOfDay(zone).withZoneSameInstant(Constant.ZONE).toLocalDateTime(),
                to.plusDays(1).atStartOfDay(zone).withZoneSameInstant(Constant.ZONE).toLocalDateTime());
        var logs = logRepository.findByOwnerIdAndGoalIdAndCanceledAtIsNullAndPracticedDateBetween(owner.getId(), goalId, from, to);
        var occurrences = occurrenceRepository.findByOwnerIdAndGoalIdAndServiceDateBetween(owner.getId(), goalId, from, to);
        Map<Integer, String> goalTitles = new HashMap<>();
        goalVersionRepository.findByGoalIdOrderByVersionAsc(goalId)
                .forEach(version -> goalTitles.put(version.getVersion(), version.getTitle()));

        List<GoalJourneyItem> items = new ArrayList<>();
        List<String> stamps = new ArrayList<>();
        for (GoalPracticeResult result : results) {
            LocalDate practicedDate = result.getPracticedAt().atZone(ZoneId.of(result.getPracticedTimeZone()))
                    .withZoneSameInstant(zone).toLocalDate();
            if (!practicedDate.isBefore(from) && !practicedDate.isAfter(to)) {
                items.add(fromResult(result, practicedDate));
                stamps.add("result:" + result.getOccurrenceId() + ":" + result.getOccurrence().getEntityVersion());
            }
        }
        for (Task task : tasks) {
            items.add(fromTask(task, goalId, zone));
            stamps.add("task:" + task.getId() + ":" + task.getUpdatedAt());
        }
        for (GoalDirectLog log : logs) {
            items.add(fromLog(log, goalTitles.get(log.getGoalVersion())));
            stamps.add("log:" + log.getId() + ":" + log.getUpdatedAt());
        }
        occurrences.forEach(o -> stamps.add("occurrence:" + o.getId() + ":" + o.getEntityVersion()
                + ":" + o.getPlan().getCurrentVersion() + ":" + o.getPlan().getStatus()));
        items.sort(ORDER);
        stamps.sort(Comparator.naturalOrder());
        GoalJourneySummary summary = summarize(items, occurrences, to);
        String version = "goal:%d:%s".formatted(goalId,
                hash(objectMapper.writeValueAsBytes(List.of(GoalResponse.from(goal), range, summary, items, stamps))));
        if (cursor != null && !version.equals(cursor.version())) {
            throw new GoalJourneyConflictException("cursor의 원본 여정이 변경되었습니다.");
        }
        int start = startIndex(items, cursor);
        int end = Math.min(start + limit, items.size());
        List<GoalJourneyItem> page = List.copyOf(items.subList(start, end));
        String next = end < items.size() ? encodeCursor(scope, version, page.getLast()) : null;
        return new GoalJourneyResponse(goalId, range, summary, page, next, version);
    }

    private GoalJourneySummary summarize(List<GoalJourneyItem> items, List<GoalPlanOccurrence> occurrences, LocalDate to) {
        List<Long> planIds = occurrences.stream().filter(o -> o.getStatus() == GoalOccurrenceStatus.PENDING)
                .map(o -> o.getPlan().getId()).distinct().toList();
        Map<Long, List<GoalPlanVersion>> versions = new HashMap<>();
        if (!planIds.isEmpty()) {
            planVersionRepository.findByPlanIdInAndEffectiveFromDateLessThanEqualOrderByVersionAsc(planIds, to)
                    .forEach(v -> versions.computeIfAbsent(v.getPlan().getId(), ignored -> new ArrayList<>()).add(v));
        }
        long pending = occurrences.stream().filter(o -> isVisiblePending(o, versions)).count();
        return new GoalJourneySummary(
                countSource(items, GoalJourneySourceType.PLAN_OCCURRENCE),
                countSource(items, GoalJourneySourceType.TASK_COMPLETION),
                countSource(items, GoalJourneySourceType.DIRECT_LOG),
                items.stream().map(GoalJourneyItem::practicedDate).distinct().count(),
                countVariant(items, ActionVariant.STANDARD), countVariant(items, ActionVariant.MINIMUM),
                countVariant(items, ActionVariant.UNKNOWN),
                occurrences.stream().filter(o -> o.getStatus() == GoalOccurrenceStatus.RESTED).count(), pending);
    }

    private boolean isVisiblePending(GoalPlanOccurrence occurrence, Map<Long, List<GoalPlanVersion>> versions) {
        if (occurrence.getStatus() != GoalOccurrenceStatus.PENDING) {
            return false;
        }
        if (occurrence.getServiceDate().isBefore(LocalDate.now(ZoneId.of(occurrence.getTimeZone())))) {
            return true;
        }
        if (occurrence.getPlan().getStatus() != GoalPlanStatus.ACTIVE) {
            return false;
        }
        GoalPlanVersion selected = null;
        for (GoalPlanVersion version : versions.getOrDefault(occurrence.getPlan().getId(), List.of())) {
            if (!version.getEffectiveFromDate().isAfter(occurrence.getServiceDate())) {
                selected = version;
            }
        }
        return selected != null && selected.isScheduledOn(occurrence.getServiceDate())
                && selected.getGoal() != null && selected.getGoal().getId().equals(occurrence.getGoalId());
    }

    private GoalJourneyItem fromResult(GoalPracticeResult result, LocalDate practicedDate) {
        GoalPlanOccurrence o = result.getOccurrence();
        String action = switch (result.getActionVariant()) {
            case STANDARD -> o.getSnapshotStandardActionText();
            case MINIMUM -> o.getSnapshotMinimumActionText();
            case UNKNOWN -> null;
        };
        String next = switch (result.getNextActionPatchKind()) {
            case KEEP -> o.getSnapshotNextAction();
            case CLEAR -> null;
            case REPLACE -> result.getNextActionValue();
        };
        return new GoalJourneyItem(GoalJourneySourceType.PLAN_OCCURRENCE, o.getId(), practicedDate,
                o.getGoalId(), o.getGoalVersion(), o.getPlan().getId(), o.getPlanVersion(), o.getSnapshotPlanName(),
                action, result.getActionVariant(), result.getAccomplishedNote(), o.getLinkedTaskId(), o.getServiceDate(),
                result.getPracticedAt(), result.getPracticedTimeZone(), o.getSnapshotGoalTitle(), next, result.difficulties());
    }

    private GoalJourneyItem fromTask(Task task, Long goalId, ZoneId zone) {
        LocalDate date = task.getCompletedAt().atZone(Constant.ZONE).withZoneSameInstant(zone).toLocalDate();
        return new GoalJourneyItem(GoalJourneySourceType.TASK_COMPLETION, task.getId(), date, goalId, null,
                null, null, task.getTitle(), null, ActionVariant.UNKNOWN, task.getDescription(), task.getId(),
                null, task.getCompletedAt(), Constant.ZONE_ID, null, null, List.of());
    }

    private GoalJourneyItem fromLog(GoalDirectLog log, String goalTitle) {
        return new GoalJourneyItem(GoalJourneySourceType.DIRECT_LOG, log.getId(), log.getPracticedDate(),
                log.getGoal().getId(), log.getGoalVersion(), null, null, log.getTitle(), null,
                ActionVariant.UNKNOWN, log.getNote(), null, null, null, log.getPracticedTimeZone(), goalTitle, null, List.of());
    }

    private long countSource(List<GoalJourneyItem> items, GoalJourneySourceType type) {
        return items.stream().filter(item -> item.sourceType() == type).count();
    }

    private long countVariant(List<GoalJourneyItem> items, ActionVariant variant) {
        return items.stream().filter(item -> item.actionVariant() == variant).count();
    }

    private ZoneId validate(LocalDate from, LocalDate to, String timeZone, int limit) {
        if (from == null || to == null || from.isAfter(to) || limit < 1 || limit > 100) {
            throw new GoalValidationException("시작일/종료일과 1~100의 limit이 필요합니다.");
        }
        try {
            // Check bounds before doing arithmetic for source queries.
            from.minusDays(2);
            to.plusDays(3);
            return ZoneId.of(timeZone == null || timeZone.isBlank() ? Constant.ZONE_ID : timeZone.trim());
        } catch (DateTimeException e) {
            throw new GoalValidationException("유효한 날짜 범위와 IANA timeZone이 필요합니다.");
        }
    }

    private int startIndex(List<GoalJourneyItem> items, Cursor cursor) {
        if (cursor == null) {
            return 0;
        }
        for (int i = 0; i < items.size(); i++) {
            GoalJourneyItem item = items.get(i);
            if (item.sourceType() == cursor.type() && item.sourceId().equals(cursor.id())
                    && item.practicedDate().equals(cursor.date())) {
                return i + 1;
            }
        }
        throw new GoalValidationException("올바르지 않은 cursor 항목입니다.");
    }

    private String encodeCursor(String scope, String version, GoalJourneyItem last) {
        String value = String.join("|", scope, version, last.practicedDate().toString(), last.sourceType().name(), last.sourceId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > 2048) {
            throw new GoalValidationException("올바르지 않은 cursor 길이입니다.");
        }
        try {
            String[] fields = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length != 5 || !fields[0].matches("[a-f0-9]{64}")
                    || !fields[1].matches("goal:[1-9][0-9]*:[a-f0-9]{64}") || Long.parseLong(fields[4]) <= 0) {
                throw new IllegalArgumentException();
            }
            return new Cursor(fields[0], fields[1], LocalDate.parse(fields[2]),
                    GoalJourneySourceType.valueOf(fields[3]), Long.parseLong(fields[4]));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new GoalValidationException("올바르지 않은 cursor입니다.");
        }
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Cursor(String scope, String version, LocalDate date, GoalJourneySourceType type, Long id) {}
}
