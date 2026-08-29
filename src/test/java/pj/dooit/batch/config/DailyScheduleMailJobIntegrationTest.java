package pj.dooit.batch.config;

import pj.dooit.mail.MailService;
import pj.dooit.task.domain.query.TaskQueryType;
import pj.dooit.task.dto.TaskQueryRequest;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@SpringBootTest(properties = {
        "app.mail.daily-summary.to=test@dooit.com",
        "spring.batch.job.enabled=false"
})
@SpringBatchTest
@ActiveProfiles("test")
class DailyScheduleMailJobIntegrationTest {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private Job dailyScheduleMailJob;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private MailService mailService;

    @BeforeEach
    void setUp() {
        jobOperatorTestUtils.setJob(dailyScheduleMailJob);
        new ResourceDatabasePopulator(
                new ClassPathResource("org/springframework/batch/core/schema-drop-h2.sql"),
                new ClassPathResource("org/springframework/batch/core/schema-h2.sql")
        ).execute(dataSource);
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("dailyScheduleMailStep 이 정상 완료된다")
    void dailyScheduleMailStep_completes() throws Exception {
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("baseDate", "2026-03-11")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution jobExecution =
                jobOperatorTestUtils.startStep("dailyScheduleMailStep", jobParameters, null);

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("dailyScheduleMailJob이 정상 완료되고 메일을 발송한다")
    void dailyScheduleMailJob_completes_and_sendsMail() throws Exception {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of(
                taskResponse(1L, "미정 일정", null, null, true)
        ));
        given(taskService.getTasks(refEq(new TaskQueryRequest(TaskQueryType.DAY, "2026-03-12"))))
                .willReturn(List.of(
                        taskResponse(2L, "회의", LocalDateTime.of(2026, 3, 12, 10, 0), null, false)
                ));
        given(taskService.getTasks(refEq(new TaskQueryRequest(TaskQueryType.WEEK, "2026-03-12"))))
                .willReturn(List.of(
                        taskResponse(3L, "개발", LocalDateTime.of(2026, 3, 12, 14, 0), LocalDateTime.of(2026, 3, 12, 16, 0), false)
                ));

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("baseDate", "2026-03-12")
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobOperatorTestUtils.startJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        then(taskService).should().getUnscheduledTasks();
        then(taskService).should().getTasks(refEq(new TaskQueryRequest(TaskQueryType.DAY, "2026-03-12")));
        then(taskService).should().getTasks(refEq(new TaskQueryRequest(TaskQueryType.WEEK, "2026-03-12")));

        then(mailService).should().sendText(
                eq("test@dooit.com"),
                eq("[Dooit] 2026-03-12 일정 요약"),
                contains("기준일: 2026-03-12")
        );
    }

    @Test
    @DisplayName("baseDate 파라미터가 없으면 Job이 실패한다")
    void dailyScheduleMailJob_withoutBaseDate_fails() throws Exception {
        // given
        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobOperatorTestUtils.startJob(jobParameters);

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        then(mailService).shouldHaveNoInteractions();
    }

    private TaskResponse taskResponse(Long id, String title, LocalDateTime startAt, LocalDateTime endAt, boolean unscheduled) {
        return new TaskResponse(id, title, null, startAt, endAt, false, unscheduled, null, null);
    }
}
