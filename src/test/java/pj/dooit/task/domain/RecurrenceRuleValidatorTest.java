package pj.dooit.task.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceRuleValidatorTest {

    @Test
    @DisplayName("RRULE은 지원 범위 안에서 대문자로 정규화된다")
    void validate_success() {
        String result = RecurrenceRuleValidator.validate(
                RecurrenceFrequency.MONTHLY,
                1,
                "freq=monthly;interval=1;bymonthday=-1;until=20261231",
                "Asia/Seoul",
                LocalDate.of(2026, 12, 31),
                null
        );

        assertThat(result).isEqualTo("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=-1;UNTIL=20261231");
    }

    @Test
    @DisplayName("RRULE FREQ와 INTERVAL은 series 필드와 일치해야 한다")
    void validate_fail_mismatchedFrequencyAndInterval() {
        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=DAILY;INTERVAL=1",
                "Asia/Seoul",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FREQ");

        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.WEEKLY,
                2,
                "FREQ=WEEKLY;INTERVAL=1",
                "Asia/Seoul",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INTERVAL");
    }

    @Test
    @DisplayName("RRULE은 지원하지 않는 키와 잘못된 BYDAY/BYMONTHDAY를 거부한다")
    void validate_fail_unsupportedValues() {
        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYHOUR=9",
                "Asia/Seoul",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");

        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=XX",
                "Asia/Seoul",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BYDAY");

        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.MONTHLY,
                1,
                "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=0",
                "Asia/Seoul",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BYMONTHDAY");
    }

    @Test
    @DisplayName("RRULE COUNT와 UNTIL은 series 종료 조건과 일치해야 하며 함께 사용할 수 없다")
    void validate_fail_endCondition() {
        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.DAILY,
                1,
                "FREQ=DAILY;INTERVAL=1;COUNT=5;UNTIL=20261231",
                "Asia/Seoul",
                LocalDate.of(2026, 12, 31),
                5
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("함께 사용할 수 없습니다");

        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.DAILY,
                1,
                "FREQ=DAILY;INTERVAL=1;COUNT=5",
                "Asia/Seoul",
                null,
                4
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COUNT");

        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.DAILY,
                1,
                "FREQ=DAILY;INTERVAL=1;UNTIL=20261231",
                "Asia/Seoul",
                LocalDate.of(2026, 12, 30),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNTIL");
    }

    @Test
    @DisplayName("timeZone은 유효한 IANA Zone ID여야 한다")
    void validate_fail_timeZone() {
        assertThatThrownBy(() -> RecurrenceRuleValidator.validate(
                RecurrenceFrequency.DAILY,
                1,
                "FREQ=DAILY;INTERVAL=1",
                "Asia/Nope",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeZone");
    }
}
