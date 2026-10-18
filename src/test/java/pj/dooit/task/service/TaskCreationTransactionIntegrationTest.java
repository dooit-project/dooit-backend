package pj.dooit.task.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pj.dooit.mail.MailService;
import pj.dooit.task.domain.RecurrenceFrequency;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.dto.TaskQuickCaptureRequest;
import pj.dooit.task.dto.TaskRecurrenceRequest;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.repository.RecurrenceSeriesRepository;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@ActiveProfiles("test")
class TaskCreationTransactionIntegrationTest {

    @Autowired
    TaskService taskService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @MockitoSpyBean
    TaskRepository taskRepository;

    @MockitoBean
    MailService mailService;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일반 생성과 빠른 기록은 Task 저장 실패 시 먼저 저장한 반복 시리즈도 롤백한다")
    void taskSaveFailure_rollsBackRecurrenceSeries(boolean quickCapture) {
        User owner = userRepository.save(new User(UUID.randomUUID() + "@example.com", "hash", "생성 검증"));
        long seriesBefore = recurrenceSeriesRepository.count();
        long tasksBefore = taskRepository.count();
        LocalDate date = LocalDate.of(2026, 10, 19);

        doAnswer(invocation -> {
            // 생성 서비스의 트랜잭션 안에서 시리즈가 실제로 저장된 뒤 실패를 발생시킨다.
            assertThat(recurrenceSeriesRepository.count()).isEqualTo(seriesBefore + 1);
            throw new IllegalStateException("task save failure");
        }).when(taskRepository).save(any(Task.class));

        assertThatThrownBy(() -> {
            if (quickCapture) {
                taskService.quickCaptureForOwner(new TaskQuickCaptureRequest(
                        "매주 월요일 운동", date, "Asia/Seoul", null, TaskType.SCHEDULE), owner);
            } else {
                taskService.createForOwner(new TaskRequest(
                        "운동", null, TaskType.SCHEDULE, date.atTime(9, 0), date.atTime(10, 0),
                        null, false, new TaskRecurrenceRequest(
                                RecurrenceFrequency.WEEKLY, 1, null, "Asia/Seoul",
                                null, null, List.of("MO"), null)), owner);
            }
        }).isInstanceOf(IllegalStateException.class).hasMessage("task save failure");

        assertThat(recurrenceSeriesRepository.count()).isEqualTo(seriesBefore);
        assertThat(taskRepository.count()).isEqualTo(tasksBefore);
        userRepository.deleteById(owner.getId());
    }
}
