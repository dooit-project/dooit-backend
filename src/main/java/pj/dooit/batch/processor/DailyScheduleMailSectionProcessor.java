package pj.dooit.batch.processor;

import pj.dooit.batch.domain.ScheduleMailSection;
import pj.dooit.batch.domain.ScheduleMailSectionContent;
import pj.dooit.batch.domain.TaskMailRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class DailyScheduleMailSectionProcessor implements ItemProcessor<ScheduleMailSection, ScheduleMailSectionContent> {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public ScheduleMailSectionContent process(ScheduleMailSection item) {
        int taskCount = item.tasks() == null ? 0 : item.tasks().size();
        log.info("[BATCH] process section start. type={}, baseDate={}, taskCount={}",
                item.type(), item.baseDate(), taskCount);

        String content = buildSectionContent(item.tasks());

        log.info("[BATCH] process section end. type={}, contentLength={}",
                item.type(), content.length());
        return new ScheduleMailSectionContent(item.type(), content);
    }

    private String buildSectionContent(List<TaskMailRow> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            log.debug("[BATCH] section has no tasks");
            return "- 없음\n";
        }

        StringBuilder sb = new StringBuilder();
        for (TaskMailRow task : tasks) {
            sb.append("- ").append(task.title());

            if (task.startAt() != null) {
                sb.append(" [").append(formatSchedule(task)).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatSchedule(TaskMailRow taskResponse) {
        if (taskResponse.startAt() == null) {
            return "미정";
        }

        if (taskResponse.endAt() == null) {
            return taskResponse.startAt().format(DATE_TIME_FORMATTER);
        }

        return taskResponse.startAt().format(DATE_TIME_FORMATTER)
                + " ~ "
                + taskResponse.endAt().format(DATE_TIME_FORMATTER);
    }
}
