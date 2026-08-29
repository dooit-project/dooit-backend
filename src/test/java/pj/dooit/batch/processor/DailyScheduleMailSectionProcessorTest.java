package pj.dooit.batch.processor;

import pj.dooit.batch.domain.ScheduleMailSection;
import pj.dooit.batch.domain.ScheduleMailSectionContent;
import pj.dooit.batch.domain.ScheduleSectionType;
import pj.dooit.batch.domain.TaskMailRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyScheduleMailSectionProcessorTest {

    private DailyScheduleMailSectionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DailyScheduleMailSectionProcessor();
    }

    @Test
    @DisplayName("tasks 가 null 이면 없음 문자열을 반환한다")
    void process_nullTasks_returnsEmptyMessage() {
        // given
        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.TODAY,
                LocalDate.of(2026, 3, 11),
                null
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(ScheduleSectionType.TODAY);
        assertThat(result.content()).isEqualTo("- 없음\n");
    }

    @Test
    @DisplayName("tasks 가 비어있으면 없음 문자열을 반환한다")
    void process_emptyTasks_returnEmptyMessage() {
        // given
        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.SEED,
                LocalDate.of(2026, 3, 11),
                List.of()
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result).isNotNull();
        assertThat(result.type()).isEqualTo(ScheduleSectionType.SEED);
        assertThat(result.content()).isEqualTo("- 없음\n");
    }

    @Test
    @DisplayName("startAt만 있으면 단일 일정 형식으로 출력한다")
    void process_singleSchedule_formatsStartAtOnly() {
        // given
        TaskMailRow task = new TaskMailRow(
                1L,
                "테스트 코드 작성",
                LocalDateTime.of(2026, 3, 11, 10, 30),
                null
        );

        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.WEEK,
                LocalDate.of(2026, 3, 11),
                List.of(task)
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result.content())
                .isEqualTo("- 테스트 코드 작성 [2026-03-11 10:30]\n");
    }

    @Test
    @DisplayName("startAt과 endAt이 있으면 기간 일정 형식으로 출력한다")
    void process_periodSchedule_formatsStartAtAndEndAt() {
        // given
        TaskMailRow task = new TaskMailRow(
                1L,
                "프로젝트 작업",
                LocalDateTime.of(2026, 3, 11, 9, 0),
                LocalDateTime.of(2026, 3, 12, 9, 0)
        );

        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.WEEK,
                LocalDate.of(2026, 3, 11),
                List.of(task)
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result.content())
                .isEqualTo("- 프로젝트 작업 [2026-03-11 09:00 ~ 2026-03-12 09:00]\n");
    }

    @Test
    @DisplayName("startAt이 null 이면 제목만 출력한다")
    void process_unscheduledTask_printsOnlyTitle() {
        // given
        TaskMailRow task = new TaskMailRow(
                1L,
                "미정 일정",
                null,
                null
        );

        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.SEED,
                LocalDate.of(2026, 3, 11),
                List.of(task)
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result.content())
                .isEqualTo("- 미정 일정\n");
    }

    @Test
    @DisplayName("여러 일정이 있으면 줄바꿈으로 이어 붙인다")
    void process_multipleTasks_appendsAllRows() {
        // given
        TaskMailRow first = new TaskMailRow(
                1L,
                "아침",
                LocalDateTime.of(2026, 3, 11, 10, 0),
                null
        );

        TaskMailRow second = new TaskMailRow(
                2L,
                "점심",
                null,
                null
        );

        TaskMailRow third = new TaskMailRow(
                3L,
                "저녁",
                LocalDateTime.of(2026, 3, 11, 18, 0),
                LocalDateTime.of(2026, 3, 11, 19, 0)
        );

        ScheduleMailSection item = new ScheduleMailSection(
                ScheduleSectionType.TODAY,
                LocalDate.of(2026, 3, 11),
                List.of(first, second, third)
        );

        // when
        ScheduleMailSectionContent result = processor.process(item);

        // then
        assertThat(result.content())
                .isEqualTo(
                        "- 아침 [2026-03-11 10:00]\n" +
                                "- 점심\n" +
                                "- 저녁 [2026-03-11 18:00 ~ 2026-03-11 19:00]\n"
                );
    }
}