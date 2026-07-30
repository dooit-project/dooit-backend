package com.todolab.task.domain.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class DateRangeTest {

    @Test
    @DisplayName("DAY 계산 - start 는 해당일 00:00, end 는 다음날 00:00이다")
    void calculate_DAY() {
        // when
        DateRange range = DateRange.ofDay("2025-11-27");

        // then
        assertThat(range.getStart()).isEqualTo(LocalDateTime.of(2025, 11, 27, 0, 0));
        assertThat(range.getEnd()).isEqualTo(LocalDateTime.of(2025, 11, 28, 0, 0));
    }

    @Test
    @DisplayName("WEEK 계산 - start 는 일요일 00:00, end 는 다음주 일요일 00:00이다")
    void calculate_WEEK() {
        // when
        DateRange range = DateRange.ofWeek("2025-12-04"); // 2025-12-04은 목요일

        // then
        assertThat(range.getStart()).isEqualTo(LocalDateTime.of(2025, 11, 30, 0, 0)); // 일요일 00:00
        assertThat(range.getEnd()).isEqualTo(LocalDateTime.of(2025, 12, 7, 0, 0));    // 다음주 일요일 00:00
    }

    @Test
    @DisplayName("MONTH 계산 - start 는 1일 00:00, end 는 다음달 1일 00:00이다")
    void calculate_MONTH() {
        // when
        DateRange range = DateRange.ofMonth("2025-11");

        // then
        assertThat(range.getStart()).isEqualTo(LocalDateTime.of(2025, 11, 1, 0, 0));
        assertThat(range.getEnd()).isEqualTo(LocalDateTime.of(2025, 12, 1, 0, 0));
    }

    @Test
    @DisplayName("[startInclusive, endExclusive) 규칙 - end 는 start 보다 항상 이후다")
    void invariant_endExclusive_after_start() {
        // given
        DateRange day = DateRange.ofDay("2025-11-27");
        DateRange week = DateRange.ofWeek("2025-11-27");
        DateRange month = DateRange.ofMonth("2025-11");

        // then
        assertThat(day.getEnd()).isAfter(day.getStart());
        assertThat(week.getEnd()).isAfter(week.getStart());
        assertThat(month.getEnd()).isAfter(month.getStart());
    }

    @Test
    @DisplayName("[startInclusive, endExclusive) 규칙 - end 는 포함되지 않으므로 startAt==end 는 범위 밖이다(개념 검증)")
    void invariant_endExclusive_is_exclusive_concept() {
        // given
        DateRange range = DateRange.ofDay("2025-11-27");
        LocalDateTime startInclusive = range.getStart();
        LocalDateTime endExclusive = range.getEnd();

        // when
        boolean includesStart = !startInclusive.isBefore(startInclusive) && startInclusive.isBefore(endExclusive); // true
        boolean includesEndExclusive = !endExclusive.isBefore(startInclusive) && endExclusive.isBefore(endExclusive); // false

        // then
        assertThat(includesStart).isTrue();
        assertThat(includesEndExclusive).isFalse();
    }

    @Test
    @DisplayName("WEEK 경계 - 연도/월이 바뀌는 주도 올바르게 계산된다")
    void calculate_WEEK_crossMonth() {
        // 2025-12-31(수) 기준: 일요일은 2025-12-28, 다음주 일요일은 2026-01-04
        DateRange range = DateRange.ofWeek("2025-12-31");

        assertThat(range.getStart()).isEqualTo(LocalDateTime.of(2025, 12, 28, 0, 0));
        assertThat(range.getEnd()).isEqualTo(LocalDateTime.of(2026, 1, 4, 0, 0));
    }

    @Test
    @DisplayName("MONTH 경계 - 연도가 바뀌는 달도 올바르게 계산된다")
    void calculate_MONTH_crossYear() {
        DateRange range = DateRange.ofMonth("2025-12");

        assertThat(range.getStart()).isEqualTo(LocalDateTime.of(2025, 12, 1, 0, 0));
        assertThat(range.getEnd()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("사용자 timezone 날짜 경계를 서비스 timezone LocalDateTime 범위로 변환한다")
    void toServiceZone_success() {
        DateRange range = DateRange.ofDay("2026-07-13");

        DateRange serviceRange = range.toServiceZone(ZoneId.of("America/New_York"));

        assertThat(serviceRange.getStart()).isEqualTo(LocalDateTime.of(2026, 7, 13, 13, 0));
        assertThat(serviceRange.getEnd()).isEqualTo(LocalDateTime.of(2026, 7, 14, 13, 0));
        assertThat(serviceRange.materializeFromInclusive()).isEqualTo(java.time.LocalDate.of(2026, 7, 13));
        assertThat(serviceRange.materializeToExclusive()).isEqualTo(java.time.LocalDate.of(2026, 7, 15));
    }
}
