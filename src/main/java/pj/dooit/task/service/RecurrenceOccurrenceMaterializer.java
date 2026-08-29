package pj.dooit.task.service;

import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializer {

    private final RecurrenceSeriesRepository recurrenceSeriesRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public void materializeForOwner(Long ownerId, LocalDate fromInclusive, LocalDate toExclusive) {
        if (ownerId == null || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            return;
        }

        recurrenceSeriesRepository.findByOwnerId(ownerId).forEach(series -> materializeSeries(series, fromInclusive, toExclusive));
    }

    @Transactional
    public void materializeForWorkspace(Long workspaceId, LocalDate fromInclusive, LocalDate toExclusive) {
        if (workspaceId == null || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            return;
        }

        recurrenceSeriesRepository.findByWorkspaceId(workspaceId).forEach(series -> materializeSeries(series, fromInclusive, toExclusive));
    }

    private void materializeSeries(RecurrenceSeries series, LocalDate fromInclusive, LocalDate toExclusive) {
        Task template = taskRepository.findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(series.getId()).stream()
                .filter(task -> task.getRecurrenceException() == null)
                .min(Comparator.comparing(Task::getOccurrenceDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (template == null) {
            return;
        }

        for (LocalDate occurrenceDate : occurrences(series, fromInclusive, toExclusive)) {
            if (taskRepository.findByRecurrenceSeriesIdAndOccurrenceDate(series.getId(), occurrenceDate).isPresent()) {
                continue;
            }
            taskRepository.save(createOccurrence(template, series, occurrenceDate));
        }
    }

    private List<LocalDate> occurrences(RecurrenceSeries series, LocalDate fromInclusive, LocalDate toExclusive) {
        LocalDate startDate = series.getRecurrenceStartAt().toLocalDate();
        LocalDate effectiveFrom = fromInclusive.isBefore(startDate) ? startDate : fromInclusive;
        LocalDate effectiveTo = series.getRecurrenceUntil() == null || series.getRecurrenceUntil().plusDays(1).isAfter(toExclusive)
                ? toExclusive
                : series.getRecurrenceUntil().plusDays(1);
        if (!effectiveFrom.isBefore(effectiveTo)) {
            return List.of();
        }

        RuleParts ruleParts = RuleParts.from(series.getRecurrenceRule());
        List<LocalDate> dates = effectiveFrom.datesUntil(effectiveTo)
                .filter(date -> matches(series, ruleParts, startDate, date))
                .toList();
        if (series.getRecurrenceCount() == null) {
            return dates;
        }

        return dates.stream()
                .filter(date -> sequenceIndex(series, ruleParts, startDate, date) < series.getRecurrenceCount())
                .toList();
    }

    private boolean matches(RecurrenceSeries series, RuleParts ruleParts, LocalDate startDate, LocalDate candidate) {
        return switch (series.getFrequency()) {
            case DAILY -> daysBetween(startDate, candidate) % series.getInterval() == 0;
            case WEEKLY -> matchesWeekly(series, ruleParts, startDate, candidate);
            case MONTHLY -> matchesMonthly(series, ruleParts, startDate, candidate);
            case YEARLY -> matchesYearly(series, ruleParts, startDate, candidate);
        };
    }

    private boolean matchesWeekly(RecurrenceSeries series, RuleParts ruleParts, LocalDate startDate, LocalDate candidate) {
        long weeks = ChronoUnit.WEEKS.between(startDate.with(DayOfWeek.MONDAY), candidate.with(DayOfWeek.MONDAY));
        if (weeks < 0 || weeks % series.getInterval() != 0) {
            return false;
        }
        Set<DayOfWeek> days = ruleParts.byDay().isEmpty() ? Set.of(startDate.getDayOfWeek()) : ruleParts.byDay();
        return days.contains(candidate.getDayOfWeek());
    }

    private boolean matchesMonthly(RecurrenceSeries series, RuleParts ruleParts, LocalDate startDate, LocalDate candidate) {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(startDate), YearMonth.from(candidate));
        if (months < 0 || months % series.getInterval() != 0) {
            return false;
        }
        List<Integer> monthDays = ruleParts.byMonthDay().isEmpty() ? List.of(startDate.getDayOfMonth()) : ruleParts.byMonthDay();
        return monthDays.stream().anyMatch(day -> matchesMonthDay(candidate, day));
    }

    private boolean matchesYearly(RecurrenceSeries series, RuleParts ruleParts, LocalDate startDate, LocalDate candidate) {
        long years = ChronoUnit.YEARS.between(startDate.withDayOfYear(1), candidate.withDayOfYear(1));
        if (years < 0 || years % series.getInterval() != 0 || candidate.getMonth() != startDate.getMonth()) {
            return false;
        }
        List<Integer> monthDays = ruleParts.byMonthDay().isEmpty() ? List.of(startDate.getDayOfMonth()) : ruleParts.byMonthDay();
        return monthDays.stream().anyMatch(day -> matchesMonthDay(candidate, day));
    }

    private boolean matchesMonthDay(LocalDate candidate, int day) {
        int lengthOfMonth = candidate.lengthOfMonth();
        int effectiveDay = day < 0 ? lengthOfMonth + day + 1 : day;
        return effectiveDay >= 1 && effectiveDay <= lengthOfMonth && candidate.getDayOfMonth() == effectiveDay;
    }

    private int sequenceIndex(RecurrenceSeries series, RuleParts ruleParts, LocalDate startDate, LocalDate candidate) {
        return (int) startDate.datesUntil(candidate.plusDays(1))
                .filter(date -> matches(series, ruleParts, startDate, date))
                .count() - 1;
    }

    private long daysBetween(LocalDate startDate, LocalDate candidate) {
        return ChronoUnit.DAYS.between(startDate, candidate);
    }

    private Task createOccurrence(Task template, RecurrenceSeries series, LocalDate occurrenceDate) {
        LocalDate templateDate = template.getOccurrenceDate() == null ? series.getRecurrenceStartAt().toLocalDate() : template.getOccurrenceDate();
        long daysToMove = ChronoUnit.DAYS.between(templateDate, occurrenceDate);
        LocalDateTime startAt = template.getStartAt() == null ? null : template.getStartAt().plusDays(daysToMove);
        LocalDateTime endAt = template.getEndAt() == null ? null : template.getEndAt().plusDays(daysToMove);
        LocalDateTime notifyAt = template.getNotifyAt() == null ? null : template.getNotifyAt().plusDays(daysToMove);

        Task occurrence = Task.builder()
                .title(template.getTitle())
                .description(template.getDescription())
                .type(template.getType())
                .startAt(startAt)
                .endAt(endAt)
                .allDay(template.isAllDay())
                .category(template.getCategory())
                .notificationEnabled(template.isNotificationEnabled())
                .notifyAt(notifyAt)
                .status(TaskStatus.TODAY)
                .targetDate(occurrenceDate)
                .owner(template.getOwner())
                .recurrenceSeries(series)
                .occurrenceDate(occurrenceDate)
                .originalOccurrenceDate(occurrenceDate)
                .build();
        if (series.getWorkspace() != null) {
            occurrence.assignWorkspace(series.getWorkspace());
        }
        return occurrence;
    }

    private record RuleParts(Set<DayOfWeek> byDay, List<Integer> byMonthDay) {

        static RuleParts from(String recurrenceRule) {
            Map<String, String> values = Arrays.stream(recurrenceRule.split(";"))
                    .map(token -> token.split("=", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0].toUpperCase(Locale.ROOT), parts -> parts[1].toUpperCase(Locale.ROOT)));

            Set<DayOfWeek> byDay = split(values.get("BYDAY")).stream()
                    .map(RuleParts::toDayOfWeek)
                    .collect(Collectors.toUnmodifiableSet());
            List<Integer> byMonthDay = split(values.get("BYMONTHDAY")).stream()
                    .map(Integer::parseInt)
                    .toList();
            return new RuleParts(byDay, byMonthDay);
        }

        private static List<String> split(String value) {
            if (value == null || value.isBlank()) {
                return List.of();
            }
            return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList();
        }

        private static DayOfWeek toDayOfWeek(String value) {
            return switch (value) {
                case "MO" -> DayOfWeek.MONDAY;
                case "TU" -> DayOfWeek.TUESDAY;
                case "WE" -> DayOfWeek.WEDNESDAY;
                case "TH" -> DayOfWeek.THURSDAY;
                case "FR" -> DayOfWeek.FRIDAY;
                case "SA" -> DayOfWeek.SATURDAY;
                case "SU" -> DayOfWeek.SUNDAY;
                default -> throw new IllegalArgumentException("지원하지 않는 BYDAY 값입니다.");
            };
        }
    }
}
