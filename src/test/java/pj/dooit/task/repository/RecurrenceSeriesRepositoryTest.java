package pj.dooit.task.repository;

import pj.dooit.support.RepositoryTestSupport;
import pj.dooit.task.domain.RecurrenceExceptionType;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.RecurrenceSeries;
import pj.dooit.task.domain.Task;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class RecurrenceSeriesRepositoryTest extends RepositoryTestSupport {

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("반복 series를 owner 범위로 저장하고 조회한다")
    void saveAndFindByOwner() {
        User owner = userRepository.save(new User("recurrence-owner@example.com", "encoded-password", "반복 사용자"));
        User otherOwner = userRepository.save(new User("recurrence-other@example.com", "encoded-password", "다른 사용자"));
        RecurrenceSeries series = new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;UNTIL=20260831",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 20, 9, 0),
                LocalDate.of(2026, 8, 31),
                null
        );

        RecurrenceSeries saved = recurrenceSeriesRepository.save(series);
        flushAndClear();

        RecurrenceSeries found = recurrenceSeriesRepository.findByIdAndOwnerId(saved.getId(), owner.getId()).orElseThrow();

        assertThat(found.getFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(found.getInterval()).isEqualTo(1);
        assertThat(found.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;UNTIL=20260831");
        assertThat(found.getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(found.getRecurrenceStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 9, 0));
        assertThat(found.getRecurrenceUntil()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(found.getRecurrenceCount()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(recurrenceSeriesRepository.findByIdAndOwnerId(saved.getId(), otherOwner.getId())).isEmpty();
    }

    @Test
    @DisplayName("반복 occurrence Task는 series와 원래 occurrence 날짜를 저장한다")
    void saveTaskWithRecurrenceSeries() {
        User owner = userRepository.save(new User("recurrence-task@example.com", "encoded-password", "반복 사용자"));
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.DAILY,
                2,
                "FREQ=DAILY;INTERVAL=2;COUNT=5",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                5
        ));
        Task task = Task.builder()
                .title("반복 운동")
                .startAt(LocalDateTime.of(2026, 7, 22, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 22, 10, 0))
                .owner(owner)
                .build();
        task.connectRecurrenceSeries(series, LocalDate.of(2026, 7, 22));
        task.markRecurrenceException(RecurrenceExceptionType.MODIFIED, LocalDate.of(2026, 7, 20));

        Long taskId = taskRepository.save(task).getId();
        flushAndClear();

        Task found = taskRepository.findById(taskId).orElseThrow();

        assertThat(found.getRecurrenceSeries().getId()).isEqualTo(series.getId());
        assertThat(found.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 7, 22));
        assertThat(found.getOriginalOccurrenceDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(found.getRecurrenceException()).isEqualTo(RecurrenceExceptionType.MODIFIED);
    }

    @Test
    @DisplayName("반복 series는 필수값과 종료 조건 범위를 검증한다")
    void validateSeries() {
        assertThatThrownBy(() -> new RecurrenceSeries(
                null,
                RecurrenceFrequency.MONTHLY,
                0,
                "FREQ=MONTHLY;INTERVAL=0",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 20, 9, 0),
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new RecurrenceSeries(
                null,
                RecurrenceFrequency.MONTHLY,
                1,
                "FREQ=MONTHLY;INTERVAL=1;UNTIL=20260719",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 20, 9, 0),
                LocalDate.of(2026, 7, 19),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
