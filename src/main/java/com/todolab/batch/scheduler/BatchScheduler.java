package com.todolab.batch.scheduler;

import com.todolab.Constant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.batch.scheduler.enabled",
        havingValue = "true"
)
public class BatchScheduler {

    private final JobOperator jobOperator;
    private final Job dailyScheduleMailJob;

//    @Scheduled(cron = "0 0 9 * * *", zone = Constant.ZONE_ID)
    @Scheduled(initialDelay = 1_000, fixedDelay = 86_400_000)
    public void runDailyScheduleMailJob() {
        try {
            String baseDate = LocalDate.now(Constant.ZONE).toString();
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("baseDate", baseDate)
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            log.info("[BATCH] dailyScheduleMailJob start. baseDate={}", baseDate);
            jobOperator.start(dailyScheduleMailJob, jobParameters);
        } catch (Exception e) {
            log.error("[BATCH] dailyScheduleMailJob 실행 실패", e);
        }
    }
}
