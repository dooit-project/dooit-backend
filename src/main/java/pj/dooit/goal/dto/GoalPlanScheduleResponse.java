package pj.dooit.goal.dto;

import pj.dooit.goal.domain.GoalPlanVersion;
import pj.dooit.goal.domain.ScheduleDay;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record GoalPlanScheduleResponse(
        LocalDate startDate,
        List<ScheduleDay> days,
        String timeZone,
        LocalTime notifyAtTime
) {
    public static GoalPlanScheduleResponse from(GoalPlanVersion version) {
        return new GoalPlanScheduleResponse(
                version.getScheduleStartDate(),
                version.scheduleDays(),
                version.getScheduleTimeZone(),
                version.getNotifyAtTime()
        );
    }
}
