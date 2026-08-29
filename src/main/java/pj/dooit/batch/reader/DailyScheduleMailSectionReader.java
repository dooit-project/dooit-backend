package pj.dooit.batch.reader;

import pj.dooit.batch.domain.ScheduleMailSection;
import pj.dooit.batch.domain.ScheduleSectionType;
import pj.dooit.batch.domain.TaskMailRow;
import pj.dooit.task.domain.query.TaskQueryType;
import pj.dooit.task.dto.TaskQueryRequest;
import pj.dooit.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DailyScheduleMailSectionReader implements ItemReader<ScheduleMailSection> {

    private final TaskService taskService;

    @Value("#{jobParameters['baseDate']}")
    private String baseDateParam;

    private ListItemReader<ScheduleMailSection> delegate;

    @Override
    public ScheduleMailSection read() {
        if (delegate == null) {
            LocalDate baseDate = getBaseDate();
            log.info("[BATCH] create delegate. baseDate={}", baseDate);
            delegate = new ListItemReader<>(createSections(baseDate));
        }

        ScheduleMailSection item = delegate.read();
        log.info("[BATCH] read result. type={}", item == null ? null : item.type());
        return item;
    }

    private LocalDate getBaseDate() {
        if (baseDateParam == null || baseDateParam.isBlank()) {
            throw new IllegalStateException("JobParameter 'baseDate' is missing in reader.");
        }
        return LocalDate.parse(baseDateParam);
    }

    private List<ScheduleMailSection> createSections(LocalDate baseDate) {
        return List.of(
                createSeedSection(baseDate),
                createTodaySection(baseDate),
                createWeekSection(baseDate)
        );
    }

    private ScheduleMailSection createSeedSection(LocalDate baseDate) {
        return new ScheduleMailSection(
                ScheduleSectionType.SEED,
                baseDate,
                TaskMailRow.from(taskService.getUnscheduledTasks())
        );
    }

    private ScheduleMailSection createTodaySection(LocalDate baseDate) {
        return new ScheduleMailSection(
                ScheduleSectionType.TODAY,
                baseDate,
                TaskMailRow.from(
                        taskService.getTasks(
                                new TaskQueryRequest(TaskQueryType.DAY, baseDate.toString())
                        )
                )
        );
    }

    private ScheduleMailSection createWeekSection(LocalDate baseDate) {
        return new ScheduleMailSection(
                ScheduleSectionType.WEEK,
                baseDate,
                TaskMailRow.from(
                        taskService.getTasks(
                                new TaskQueryRequest(TaskQueryType.WEEK, baseDate.toString())
                        )
                )
        );
    }
}
