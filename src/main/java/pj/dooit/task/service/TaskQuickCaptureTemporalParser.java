package pj.dooit.task.service;

import pj.dooit.task.exception.TaskValidationException;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 날짜·시각·주간 반복 표현만 해석하며 Task 유형과 저장 정책은 결정하지 않는다. */
final class TaskQuickCaptureTemporalParser {

    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern KOREAN_DATE = Pattern.compile("(?<!\\d)(?:(\\d{4})년\\s*)?(\\d{1,2})월\\s*(\\d{1,2})일(?!\\d)");
    private static final Pattern SLASH_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b");
    private static final Pattern KOREAN_TIME = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})시(?:\\s*(?:(\\d{1,2})분|반))?");
    private static final Pattern COLON_TIME = Pattern.compile("(오전|오후)?\\s*\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern WEEKLY = Pattern.compile("매주\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern RELATIVE_WEEK = Pattern.compile("(이번\\s*주|이번주|다음\\s*주|다음주|담주|다다음\\s*주|다다음주|다담주)\\s*(월요일|화요일|수요일|목요일|금요일|토요일|일요일|월|화|수|목|금|토|일)");
    private static final Pattern SINGLE_WEEKDAY = Pattern.compile("(월요일|화요일|수요일|목요일|금요일|토요일|일요일|(?<![가-힣])(월|화|수|목|금|토|일)(?![가-힣]))");

    ParsedTemporal parse(String text, LocalDate referenceDate) {
        List<String> consumedTokens = new ArrayList<>();
        ParsedDate date = parseDate(text, referenceDate, consumedTokens);
        ParsedWeekly weekly = parseWeekly(text, consumedTokens);
        ParsedTime time = parseTime(text, consumedTokens);
        return new ParsedTemporal(date.date(), time.time(), weekly, List.copyOf(consumedTokens));
    }

    private ParsedDate parseDate(String text, LocalDate referenceDate, List<String> consumedTokens) {
        if (text.contains("오늘")) {
            consumedTokens.add("오늘");
            return new ParsedDate(referenceDate);
        }
        String dayAfterTomorrowToken = findToken(text, "내일모레", "낼모레", "모레");
        if (dayAfterTomorrowToken != null) {
            consumedTokens.add(dayAfterTomorrowToken);
            return new ParsedDate(referenceDate.plusDays(2));
        }
        String tomorrowToken = findToken(text, "내일", "낼");
        if (tomorrowToken != null) {
            consumedTokens.add(tomorrowToken);
            return new ParsedDate(referenceDate.plusDays(1));
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
            int weeksToAdd = switch (relativeWeek.group(1).replace(" ", "")) {
                case "다음주", "담주" -> 1;
                case "다다음주", "다담주" -> 2;
                default -> 0;
            };
            weekStart = weekStart.plusWeeks(weeksToAdd);
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
        Matcher matcher = KOREAN_TIME.matcher(text);
        if (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(2));
            int minute = "반".equals(matcher.group().substring(matcher.group().length() - 1))
                    ? 30
                    : matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            String meridiem = matcher.group(1);
            LocalTime time = toLocalTime(hour, minute, meridiem);

            consumedTokens.add(matcher.group());
            return new ParsedTime(time);
        }

        matcher = COLON_TIME.matcher(text);
        if (!matcher.find()) {
            return new ParsedTime(null);
        }

        int hour = Integer.parseInt(matcher.group(2));
        int minute = Integer.parseInt(matcher.group(3));
        String meridiem = matcher.group(1);
        LocalTime time = toLocalTime(hour, minute, meridiem);

        consumedTokens.add(matcher.group());
        return new ParsedTime(time);
    }

    private LocalTime toLocalTime(int hour, int minute, String meridiem) {
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
        return LocalTime.of(hour, minute);
    }

    private String findToken(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return token;
            }
        }
        return null;
    }

    record ParsedTemporal(LocalDate date, LocalTime time, ParsedWeekly weekly, List<String> consumedTokens) {
    }

    private record ParsedDate(LocalDate date) {
    }

    private record ParsedTime(LocalTime time) {
    }

    record ParsedWeekly(DayOfWeek dayOfWeek, String byDay) {
    }
}
