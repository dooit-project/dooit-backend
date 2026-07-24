package com.todolab.task.service;

import com.todolab.config.QuerydslConfig;
import com.todolab.support.RepositoryTestSupport;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, RecurrenceOccurrenceMaterializer.class})
@ActiveProfiles("test")
class RecurrenceOccurrenceMaterializerTest extends RepositoryTestSupport {

    @Autowired
    RecurrenceOccurrenceMaterializer materializer;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("반복 series template Task 기준으로 조회 범위의 occurrence Task를 생성한다")
    void materializeForOwner_createsMissingOccurrences() {
        User owner = userRepository.save(new User("materialize@example.com", "encoded-password", "반복 사용자"));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=3",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                null,
                3
        ));
        Task template = Task.builder()
                .title("주간 회의")
                .description("반복")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 7, 6, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 6, 10, 0))
                .category("업무")
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2026, 7, 6))
                .originalOccurrenceDate(LocalDate.of(2026, 7, 6))
                .build();
        taskRepository.save(template);
        flushAndClear();

        materializer.materializeForOwner(owner.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        materializer.materializeForOwner(owner.getId(), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1));
        flushAndClear();

        List<Task> tasks = taskRepository.findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(series.getId());

        assertThat(tasks).hasSize(3);
        assertThat(tasks).extracting(Task::getOccurrenceDate)
                .containsExactly(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20));
        assertThat(tasks.get(1).getTitle()).isEqualTo("주간 회의");
        assertThat(tasks.get(1).getStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 13, 9, 0));
        assertThat(tasks.get(1).getEndAt()).isEqualTo(LocalDateTime.of(2026, 7, 13, 10, 0));
        assertThat(tasks.get(1).getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(tasks.get(1).getTargetDate()).isEqualTo(LocalDate.of(2026, 7, 13));
    }

    @Test
    @DisplayName("월말 반복은 BYMONTHDAY=-1로 각 월의 마지막 날 occurrence를 생성한다")
    void materializeForOwner_monthEndByMonthDay() {
        User owner = userRepository.save(new User("materialize-month-end@example.com", "encoded-password", "반복 사용자"));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.MONTHLY,
                1,
                "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=-1;COUNT=3",
                "Asia/Seoul",
                LocalDateTime.of(2026, 1, 31, 9, 0),
                null,
                3
        ));
        taskRepository.save(Task.builder()
                .title("월말 정산")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 1, 31, 9, 0))
                .endAt(LocalDateTime.of(2026, 1, 31, 10, 0))
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2026, 1, 31))
                .originalOccurrenceDate(LocalDate.of(2026, 1, 31))
                .build());
        flushAndClear();

        materializer.materializeForOwner(owner.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 1));
        flushAndClear();

        List<Task> tasks = taskRepository.findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(series.getId());

        assertThat(tasks).extracting(Task::getOccurrenceDate)
                .containsExactly(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
        assertThat(tasks).extracting(Task::getStartAt)
                .containsExactly(
                        LocalDateTime.of(2026, 1, 31, 9, 0),
                        LocalDateTime.of(2026, 2, 28, 9, 0),
                        LocalDateTime.of(2026, 3, 31, 9, 0)
                );
    }

    @Test
    @DisplayName("윤년 2월 29일 yearly 반복은 다음 윤년 occurrence만 생성한다")
    void materializeForOwner_leapDayYearly() {
        User owner = userRepository.save(new User("materialize-leap@example.com", "encoded-password", "반복 사용자"));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.YEARLY,
                1,
                "FREQ=YEARLY;INTERVAL=1;COUNT=2",
                "Asia/Seoul",
                LocalDateTime.of(2028, 2, 29, 9, 0),
                null,
                2
        ));
        taskRepository.save(Task.builder()
                .title("윤년 점검")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2028, 2, 29, 9, 0))
                .endAt(LocalDateTime.of(2028, 2, 29, 10, 0))
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2028, 2, 29))
                .originalOccurrenceDate(LocalDate.of(2028, 2, 29))
                .build());
        flushAndClear();

        materializer.materializeForOwner(owner.getId(), LocalDate.of(2028, 1, 1), LocalDate.of(2033, 1, 1));
        flushAndClear();

        List<Task> tasks = taskRepository.findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(series.getId());

        assertThat(tasks).extracting(Task::getOccurrenceDate)
                .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2032, 2, 29));
    }

    @Test
    @DisplayName("DST가 있는 time zone 경계에서도 occurrence의 로컬 시간을 유지한다")
    void materializeForOwner_timeZoneBoundaryKeepsLocalTime() {
        User owner = userRepository.save(new User("materialize-time-zone@example.com", "encoded-password", "반복 사용자"));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=2",
                "America/New_York",
                LocalDateTime.of(2026, 3, 2, 9, 0),
                null,
                2
        ));
        taskRepository.save(Task.builder()
                .title("DST 경계 회의")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 3, 2, 9, 0))
                .endAt(LocalDateTime.of(2026, 3, 2, 10, 0))
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(LocalDate.of(2026, 3, 2))
                .originalOccurrenceDate(LocalDate.of(2026, 3, 2))
                .build());
        flushAndClear();

        materializer.materializeForOwner(owner.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 16));
        flushAndClear();

        List<Task> tasks = taskRepository.findByRecurrenceSeriesIdOrderByOccurrenceDateAscIdAsc(series.getId());

        assertThat(tasks).extracting(Task::getOccurrenceDate)
                .containsExactly(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9));
        assertThat(tasks).extracting(Task::getStartAt)
                .containsExactly(LocalDateTime.of(2026, 3, 2, 9, 0), LocalDateTime.of(2026, 3, 9, 9, 0));
    }
}
