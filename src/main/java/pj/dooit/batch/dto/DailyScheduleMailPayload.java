package pj.dooit.batch.dto;

import pj.dooit.task.domain.Task;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class DailyScheduleMailPayload {

    private final LocalDate baseDate;
    private final List<Task> seedTasks;
    private final List<Task> todayTasks;
    private final List<Task> weekTasks;
}
