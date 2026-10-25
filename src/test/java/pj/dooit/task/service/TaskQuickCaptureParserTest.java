package pj.dooit.task.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskQuickCaptureRequest;
import pj.dooit.task.exception.TaskValidationException;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskQuickCaptureParserTest {

    private final TaskQuickCaptureParser parser = new TaskQuickCaptureParser();
    private final LocalDate referenceDate = LocalDate.of(2026, 10, 18);

    @Test
    @DisplayName("날짜와 주간 반복이 함께 있으면 기존 정책대로 반복 첫 날짜를 적용한다")
    void weeklyDateOverridesExplicitDate() {
        var result = parse("모레 매주 월요일 오전 9시 운동", TaskType.SCHEDULE);

        assertThat(result.parsedDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(result.parsedTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.parsedByDays()).containsExactly("MO");
        assertThat(result.taskRequest().title()).isEqualTo("운동");
        assertThat(result.taskRequest().startAt()).isEqualTo(referenceDate.plusDays(1).atTime(9, 0));
        assertThat(result.taskRequest().endAt()).isEqualTo(referenceDate.plusDays(1).atTime(10, 0));
    }

    @Test
    @DisplayName("시각만 해석한 자동 일정은 기준 날짜를 사용한다")
    void timeOnlyUsesReferenceDate() {
        var result = parse("3시 반 회의", null);

        assertThat(result.parsedType()).isEqualTo(TaskType.SCHEDULE);
        assertThat(result.parsedDate()).isEqualTo(referenceDate);
        assertThat(result.taskRequest().startAt()).isEqualTo(referenceDate.atTime(15, 30));
        assertThat(result.taskRequest().allDay()).isFalse();
        assertThat(result.taskRequest().title()).isEqualTo("회의");
    }

    @Test
    @DisplayName("날짜만 해석하면 선택 유형에 따라 계획일 또는 종일 구간으로 적용한다")
    void sameDateProducesDifferentTodoAndScheduleFields() {
        var todo = parse("내일 공부", TaskType.TODO);
        var schedule = parse("내일 공부", TaskType.SCHEDULE);

        assertThat(todo.parsedDate()).isEqualTo(referenceDate.plusDays(1));
        assertThat(todo.taskRequest().startAt()).isNull();
        assertThat(todo.taskRequest().endAt()).isNull();
        assertThat(todo.taskRequest().allDay()).isFalse();
        assertThat(schedule.parsedDate()).isEqualTo(todo.parsedDate());
        assertThat(schedule.taskRequest().startAt()).isEqualTo(referenceDate.plusDays(1).atStartOfDay());
        assertThat(schedule.taskRequest().endAt()).isEqualTo(referenceDate.plusDays(2).atStartOfDay());
        assertThat(schedule.taskRequest().allDay()).isTrue();
    }

    @Test
    @DisplayName("IDEA는 잘못된 날짜와 시각도 해석하지 않고 원문으로 보존한다")
    void ideaBypassesTemporalValidation() {
        String text = "2026-02-30 25:99 매주 월요일";
        var result = parse(text, TaskType.IDEA);

        assertThat(result.taskRequest().title()).isEqualTo(text);
        assertThat(result.parsed()).isFalse();
        assertThat(result.parsedDate()).isNull();
        assertThat(result.parsedTime()).isNull();
        assertThat(result.parsedByDays()).isEmpty();
        assertThat(result.taskRequest().recurrence()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-02-30 25:99 공부|TODO|올바르지 않은 날짜입니다.",
            "내일 25:99 공부|TODO|올바르지 않은 시간입니다.",
            "내일 3시 공부|TODO|TODO 빠른 등록은 시각 또는 반복 표현을 지원하지 않습니다. 날짜만 입력해주세요.",
            "회의 준비|SCHEDULE|SCHEDULE 빠른 등록에는 날짜, 시각 또는 지원하는 반복 표현이 필요합니다."
    })
    @DisplayName("날짜와 시각 해석 오류를 유형 조합 오류보다 먼저 반환한다")
    void preservesValidationOrder(String text, TaskType type, String message) {
        assertThatThrownBy(() -> parse(text, type))
                .isInstanceOfSatisfying(TaskValidationException.class,
                        error -> assertThat(error.getDetail()).isEqualTo(message));
    }

    private TaskQuickCaptureParser.ParsedQuickCapture parse(String text, TaskType type) {
        return parser.parse(new TaskQuickCaptureRequest(text, referenceDate, "Asia/Seoul", null, type), null);
    }
}
