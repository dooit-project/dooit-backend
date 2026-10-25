package pj.dooit.task.service;

import pj.dooit.Constant;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskQuickCaptureRequest;
import pj.dooit.task.dto.TaskRecurrenceRequest;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.user.domain.User;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
public class TaskQuickCaptureParser {

    private static final int TITLE_MAX_LENGTH = 30;
    private static final int DESCRIPTION_MAX_LENGTH = 300;
    private final TaskQuickCaptureTemporalParser temporalParser = new TaskQuickCaptureTemporalParser();

    public ParsedQuickCapture parse(TaskQuickCaptureRequest request, User owner) {
        String originalText = normalizeText(request.text());
        ZoneId zoneId = resolveZoneId(request.timeZone(), owner);
        LocalDate referenceDate = request.referenceDate() == null
                ? LocalDate.now(zoneId)
                : request.referenceDate();

        if (request.requestedType() == TaskType.IDEA) {
            String title = truncate(originalText, TITLE_MAX_LENGTH);
            String description = originalText.length() > TITLE_MAX_LENGTH ? originalText : null;
            return new ParsedQuickCapture(
                    new TaskRequest(title, description, TaskType.IDEA, null, null,
                            request.defaultCategory(), false, null),
                    false, originalText, null, null, TaskType.IDEA, null, List.of(), zoneId.getId()
            );
        }

        var temporal = temporalParser.parse(originalText, referenceDate);
        var parsedWeekly = temporal.weekly();
        LocalDate effectiveDate = temporal.date();
        TaskRecurrenceRequest recurrence = null;
        if (parsedWeekly != null) {
            effectiveDate = referenceDate.with(TemporalAdjusters.nextOrSame(parsedWeekly.dayOfWeek()));
            recurrence = new TaskRecurrenceRequest(
                    RecurrenceFrequency.WEEKLY,
                    1,
                    null,
                    zoneId.getId(),
                    null,
                    null,
                    List.of(parsedWeekly.byDay()),
                    null
            );
        }

        boolean parsed = effectiveDate != null || temporal.time() != null || recurrence != null;
        TaskType type = request.requestedType() == null
                ? (parsed ? TaskType.SCHEDULE : TaskType.TODO)
                : request.requestedType();
        if (type == TaskType.TODO && (temporal.time() != null || recurrence != null)) {
            throw new TaskValidationException("TODO 빠른 등록은 시각 또는 반복 표현을 지원하지 않습니다. 날짜만 입력해주세요.");
        }
        if (type == TaskType.SCHEDULE && !parsed) {
            throw new TaskValidationException("SCHEDULE 빠른 등록에는 날짜, 시각 또는 지원하는 반복 표현이 필요합니다.");
        }
        boolean allDay = type == TaskType.SCHEDULE && temporal.time() == null;
        LocalDateTime startAt = null;
        LocalDateTime endAt = null;

        if (parsed && type == TaskType.SCHEDULE) {
            LocalDate date = effectiveDate == null ? referenceDate : effectiveDate;
            if (temporal.time() == null) {
                startAt = date.atStartOfDay();
                endAt = date.plusDays(1).atStartOfDay();
            } else {
                startAt = date.atTime(temporal.time());
                endAt = startAt.plusHours(1);
            }
        }

        String title = buildTitle(originalText, temporal.consumedTokens());
        String description = null;
        if (title.length() > TITLE_MAX_LENGTH) {
            title = title.substring(0, TITLE_MAX_LENGTH);
            description = truncate(originalText, DESCRIPTION_MAX_LENGTH);
        }

        TaskRequest taskRequest = new TaskRequest(
                title,
                description,
                type,
                startAt,
                endAt,
                request.defaultCategory(),
                allDay,
                recurrence
        );

        return new ParsedQuickCapture(
                taskRequest,
                parsed,
                originalText,
                parsed ? (effectiveDate == null ? referenceDate : effectiveDate) : null,
                temporal.time(),
                type,
                recurrence == null ? null : RecurrenceFrequency.WEEKLY,
                parsedWeekly == null ? List.of() : List.of(parsedWeekly.byDay()),
                zoneId.getId()
        );
    }

    private String normalizeText(String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new TaskValidationException("빠른 등록 원문은 필수값입니다.");
        }
        return normalized;
    }

    private ZoneId resolveZoneId(String requestedTimeZone, User owner) {
        String zoneId = requestedTimeZone;
        if (zoneId == null || zoneId.isBlank()) {
            zoneId = owner == null ? Constant.ZONE_ID : owner.getTimeZone();
        }
        if (zoneId == null || zoneId.isBlank()) {
            zoneId = Constant.ZONE_ID;
        }

        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException e) {
            throw new TaskValidationException("올바르지 않은 timezone입니다.");
        }
    }

    private String buildTitle(String originalText, List<String> consumedTokens) {
        String title = originalText;
        for (String token : consumedTokens) {
            title = title.replace(token, " ");
        }
        title = title.trim().replaceAll("\\s+", " ");
        return title.isBlank() ? originalText : title;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record ParsedQuickCapture(
            TaskRequest taskRequest,
            boolean parsed,
            String originalText,
            LocalDate parsedDate,
            LocalTime parsedTime,
            TaskType parsedType,
            RecurrenceFrequency parsedRecurrenceFrequency,
            List<String> parsedByDays,
            String timeZone
    ) {
    }

}
