package pj.dooit.goal.dto;

import pj.dooit.goal.domain.ScheduleDay;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record GoalPlanScheduleRequest(
        @NotNull
        LocalDate startDate,

        @NotEmpty
        @Size(max = 7)
        List<ScheduleDay> days,

        @Size(max = 50)
        String timeZone,

        LocalTime notifyAtTime
) {
}
