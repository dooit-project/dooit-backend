package pj.dooit.task.domain.query;

import pj.dooit.Constant;
import lombok.Getter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

@Getter
public class DateRange {

    private final LocalDateTime start; // Inclusive
    private final LocalDateTime end;   // Exclusive

    private DateRange(LocalDateTime start, LocalDateTime end) {
        this.start = start;
        this.end = end;
    }

    public static DateRange of(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("DateRange는 start < end 이어야 합니다.");
        }
        return new DateRange(start, end);
    }

    public static DateRange ofDay(String date) {
        LocalDate d = LocalDate.parse(date);
        LocalDateTime start = d.atStartOfDay();                         // 00:00
        LocalDateTime end = d.plusDays(1).atStartOfDay();    // 다음날 00:00
        return new DateRange(start, end);
    }

    public static DateRange ofWeek(String date) {
        LocalDate d = LocalDate.parse(date);
        LocalDate startDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));      // 일요일
        LocalDateTime start = startDate.atStartOfDay();      // 00:00
        LocalDateTime end = start.plusDays(7);               // 다음주 일요일 00:00
        return new DateRange(start, end);
    }

    public static DateRange ofMonth(String date) {
        YearMonth ym = YearMonth.parse(date);
        LocalDateTime start = ym.atDay(1).atStartOfDay();                           // 1일 00:00
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();   // 다음달 1일 00:00 (Exclusive)
        return new DateRange(start, end);
    }

    public DateRange toServiceZone(ZoneId sourceZone) {
        if (sourceZone == null || Constant.ZONE.equals(sourceZone)) {
            return this;
        }
        LocalDateTime serviceStart = start.atZone(sourceZone)
                .withZoneSameInstant(Constant.ZONE)
                .toLocalDateTime();
        LocalDateTime serviceEnd = end.atZone(sourceZone)
                .withZoneSameInstant(Constant.ZONE)
                .toLocalDateTime();
        return new DateRange(serviceStart, serviceEnd);
    }

    public LocalDate materializeFromInclusive() {
        return start.toLocalDate();
    }

    public LocalDate materializeToExclusive() {
        return end.toLocalTime().equals(LocalTime.MIDNIGHT)
                ? end.toLocalDate()
                : end.toLocalDate().plusDays(1);
    }
}
