package pj.dooit.batch.domain;

import java.time.LocalDate;
import java.util.List;

public record ScheduleMailSection(
        ScheduleSectionType type,
        LocalDate baseDate,
        List<TaskMailRow> tasks
) {
}
