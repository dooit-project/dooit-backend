package pj.dooit.personalsystem.dto;

import pj.dooit.personalsystem.domain.ScheduleDay;
import pj.dooit.personalsystem.domain.PersonalSystemVersion;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PersonalSystemScheduleResponse(
        LocalDate startDate,
        List<ScheduleDay> days,
        String timeZone,
        LocalTime notifyAtTime
) {
    public static PersonalSystemScheduleResponse from(PersonalSystemVersion version) {
        return new PersonalSystemScheduleResponse(
                version.getScheduleStartDate(),
                version.scheduleDays(),
                version.getScheduleTimeZone(),
                version.getNotifyAtTime()
        );
    }
}
