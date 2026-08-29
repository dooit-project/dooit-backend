package pj.dooit.batch.config;

import pj.dooit.batch.domain.ScheduleMailSection;
import pj.dooit.batch.domain.ScheduleMailSectionContent;
import pj.dooit.batch.processor.DailyScheduleMailSectionProcessor;
import pj.dooit.batch.reader.DailyScheduleMailSectionReader;
import pj.dooit.batch.writer.DailyScheduleMailWriter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailyScheduleMailJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    private final DailyScheduleMailSectionReader reader;
    private final DailyScheduleMailSectionProcessor processor;
    private final DailyScheduleMailWriter writer;

    @PostConstruct
    public void logJobRepository() {
        log.info("jobRepository class = {}", jobRepository.getClass().getName());
    }

    @Bean
    public Job dailyScheduleMailJob() {
        return new JobBuilder("dailyScheduleMailJob", jobRepository)
                .start(dailyScheduleMailStep())
                .build();
    }

    @Bean
    public Step dailyScheduleMailStep() {
        return new StepBuilder("dailyScheduleMailStep", jobRepository)
                .<ScheduleMailSection, ScheduleMailSectionContent>chunk(3)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .build();
    }
}
