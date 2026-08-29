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

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TaskQuickCaptureParser {

    private static final int TITLE_MAX_LENGTH = 30;
    private static final int DESCRIPTION_MAX_LENGTH = 300;
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern KOREAN_DATE = Pattern.compile("(?<!\\d)(?:(\\d{4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일(?!\\d)");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
    private static final Pattern TIME = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(\\d{1,2})분)?");
    private static final Pattern WEEKLY = Pattern.compile("매주\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern RELATIVE_WEEK = Pattern.compile("(이번\\s*주|다음\\s*주)\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern SINGLE_WEEKDAY = Pattern.compile("(월요일|화요일|수요일|목요일|금요일|토요일|일요일|(?<![가-힣])(월|화|수|목|금|토|일)(?![가-힣]))");

    public ParsedQuickCapture parse(TaskQuickCaptureRequest request, User owner) {
        String originalText = normalizeText(request.text());
        ZoneId zoneId = resolveZoneId(request.timeZone(), owner);
        LocalDate referenceDate = request.referenceDate() == null
                ? LocalDate.now(zoneId)
                : request.referenceDate();

        List<String> consumedTokens = new ArrayList<>();
        ParsedDate parsedDate = parseDate(originalText, referenceDate, consumedTokens);
        ParsedWeekly parsedWeekly = parseWeekly(originalText, consumedTokens);
        ParsedTime parsedTime = parseTime(originalText, consumedTokens);

        LocalDate effectiveDate = parsedDate.date();
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

        boolean parsed = effectiveDate != null || parsedTime.time() != null || recurrence != null;
        TaskType type = parsed ? TaskType.SCHEDULE : TaskType.TODO;
        boolean allDay = parsed && parsedTime.time() == null;
        LocalDateTime startAt = null;
        LocalDateTime endAt = null;

        if (parsed) {
            LocalDate date = effectiveDate == null ? referenceDate : effectiveDate;
            if (parsedTime.time() == null) {
                startAt = date.atStartOfDay();
                endAt = date.plusDays(1).atStartOfDay();
            } else {
                startAt = date.atTime(parsedTime.time());
                endAt = startAt.plusHours(1);
            }
        }

        String title = buildTitle(originalText, consumedTokens);
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
                parsedTime.time(),
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

    private ParsedDate parseDate(String text, LocalDate referenceDate, List<String> consumedTokens) {
        if (text.contains("오늘")) {
            consumedTokens.add("오늘");
            return new ParsedDate(referenceDate);
        }
        if (text.contains("내일")) {
            consumedTokens.add("내일");
            return new ParsedDate(referenceDate.plusDays(1));
        }
        if (text.contains("모레")) {
            consumedTokens.add("모레");
            return new ParsedDate(referenceDate.plusDays(2));
        }

        Matcher isoDate = ISO_DATE.matcher(text);
        if (isoDate.find()) {
            consumedTokens.add(isoDate.group());
            try {
                return new ParsedDate(LocalDate.parse(isoDate.group()));
            } catch (DateTimeException e) {
                throw new TaskValidationException("올바르지 않은 날짜입니다.");
            }
        }

        Matcher koreanDate = KOREAN_DATE.matcher(text);
        if (koreanDate.find()) {
            consumedTokens.add(koreanDate.group());
            int year = koreanDate.group(1) == null
                    ? referenceDate.getYear()
                    : Integer.parseInt(koreanDate.group(1));
            int month = Integer.parseInt(koreanDate.group(2));
            int day = Integer.parseInt(koreanDate.group(3));
            try {
                return new ParsedDate(LocalDate.of(year, month, day));
            } catch (DateTimeException e) {
                throw new TaskValidationException("올바르지 않은 날짜입니다.");
            }
        }

        Matcher slashDate = SLASH_DATE.matcher(text);
        if (slashDate.find()) {
            consumedTokens.add(slashDate.group());
            int month = Integer.parseInt(slashDate.group(1));
            int day = Integer.parseInt(slashDate.group(2));
            try {
                return new ParsedDate(LocalDate.of(referenceDate.getYear(), month, day));
            } catch (DateTimeException e) {
                throw new TaskValidationException("올바르지 않은 날짜입니다.");
            }
        }

        Matcher relativeWeek = RELATIVE_WEEK.matcher(text);
        if (relativeWeek.find()) {
            consumedTokens.add(relativeWeek.group());
            LocalDate weekStart = referenceDate.with(DayOfWeek.MONDAY);
            if (relativeWeek.group(1).replace(" ", "").equals("다음주")) {
                weekStart = weekStart.plusWeeks(1);
            }
            return new ParsedDate(weekStart.with(toDayOfWeek(relativeWeek.group(2))));
        }

        Matcher weekday = SINGLE_WEEKDAY.matcher(text);
        if (weekday.find() && !isWeeklyToken(text, weekday.start())) {
            consumedTokens.add(weekday.group());
            return new ParsedDate(referenceDate.with(TemporalAdjusters.nextOrSame(toDayOfWeek(weekday.group()))));
        }

        return new ParsedDate(null);
    }

    private ParsedWeekly parseWeekly(String text, List<String> consumedTokens) {
        Matcher matcher = WEEKLY.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        consumedTokens.add(matcher.group());
        DayOfWeek dayOfWeek = toDayOfWeek(matcher.group(1));
        return new ParsedWeekly(dayOfWeek, toByDay(dayOfWeek));
    }

    private boolean isWeeklyToken(String text, int tokenStart) {
        int prefixStart = Math.max(0, tokenStart - 3);
        return text.substring(prefixStart, tokenStart).contains("매주");
    }

    private DayOfWeek toDayOfWeek(String value) {
        return switch (value) {
            case "월요일", "월" -> DayOfWeek.MONDAY;
            case "화요일", "화" -> DayOfWeek.TUESDAY;
            case "수요일", "수" -> DayOfWeek.WEDNESDAY;
            case "목요일", "목" -> DayOfWeek.THURSDAY;
            case "금요일", "금" -> DayOfWeek.FRIDAY;
            case "토요일", "토" -> DayOfWeek.SATURDAY;
            case "일요일", "일" -> DayOfWeek.SUNDAY;
            default -> throw new TaskValidationException("올바르지 않은 요일입니다.");
        };
    }

    private String toByDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "MO";
            case TUESDAY -> "TU";
            case WEDNESDAY -> "WE";
            case THURSDAY -> "TH";
            case FRIDAY -> "FR";
            case SATURDAY -> "SA";
            case SUNDAY -> "SU";
        };
    }

    private ParsedTime parseTime(String text, List<String> consumedTokens) {
        Matcher matcher = TIME.matcher(text);
        if (!matcher.find()) {
            return new ParsedTime(null);
        }

        int hour = Integer.parseInt(matcher.group(2));
        int minute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        String meridiem = matcher.group(1);

        if (minute > 59 || hour > 23 || (meridiem != null && hour > 12)) {
            throw new TaskValidationException("올바르지 않은 시간입니다.");
        }
        if ("오후".equals(meridiem) && hour < 12) {
            hour += 12;
        }
        if ("오전".equals(meridiem) && hour == 12) {
            hour = 0;
        }
        if (meridiem == null && hour >= 1 && hour <= 7) {
            hour += 12;
        }

        consumedTokens.add(matcher.group());
        return new ParsedTime(LocalTime.of(hour, minute));
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

    private record ParsedDate(LocalDate date) {
    }

    private record ParsedTime(LocalTime time) {
    }

    private record ParsedWeekly(DayOfWeek dayOfWeek, String byDay) {
    }
}
