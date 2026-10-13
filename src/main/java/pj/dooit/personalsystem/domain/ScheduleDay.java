package pj.dooit.personalsystem.domain;

import java.time.DayOfWeek;

public enum ScheduleDay {
    MO(DayOfWeek.MONDAY),
    TU(DayOfWeek.TUESDAY),
    WE(DayOfWeek.WEDNESDAY),
    TH(DayOfWeek.THURSDAY),
    FR(DayOfWeek.FRIDAY),
    SA(DayOfWeek.SATURDAY),
    SU(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    ScheduleDay(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public static ScheduleDay from(DayOfWeek dayOfWeek) {
        for (ScheduleDay value : values()) {
            if (value.dayOfWeek == dayOfWeek) {
                return value;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 요일입니다.");
    }
}
