package com.todolab.batch.scheduler;

import com.todolab.Constant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.*;

class BatchSchedulerTest {

    private JobOperator jobOperator;
    private Job dailyScheduleMailJob;
    private BatchScheduler batchScheduler;

    @BeforeEach
    void setUp() {
        jobOperator = mock(JobOperator.class);
        dailyScheduleMailJob = mock(Job.class);
        batchScheduler = new BatchScheduler(jobOperator, dailyScheduleMailJob);
    }

    @Test
    @DisplayName("스케줄러 실행 시 dailyScheduleMailJob을 실행한다")
    void runDailyScheduleMailJob_startsJob() throws Exception {
        // given
        ArgumentCaptor<JobParameters> jobParametersCaptor = ArgumentCaptor.forClass(JobParameters.class);
        String expectedBaseDate = LocalDate.now(Constant.ZONE).toString();

        // when
        batchScheduler.runDailyScheduleMailJob();

        // then
        then(jobOperator).should()
                .start(any(Job.class), jobParametersCaptor.capture());

        JobParameters jobParameters = jobParametersCaptor.getValue();
        assertThat(jobParameters.getString("baseDate")).isEqualTo(expectedBaseDate);
        assertThat(jobParameters.getLong("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Job 실행 중 예외가 발생해도 메서드 밖으로 예외를 던지지 않는다")
    void runDailyscheduleMailJob_whenExceptionOccurs_doesNotThrow() throws Exception {
        // given
        willThrow(new RuntimeException("batch start failed"))
                .given(jobOperator)
                .start(any(Job.class), any(JobParameters.class));

        // when & then
        assertThatCode(() -> batchScheduler.runDailyScheduleMailJob())
                .doesNotThrowAnyException();
        then(jobOperator).should()
                .start(any(Job.class), any(JobParameters.class));
    }
}
