package pj.dooit.goal.domain;

import java.time.DayOfWeek;

public enum ScheduleDay {
    MO,
    TU,
    WE,
    TH,
    FR,
    SA,
    SU;

    public static ScheduleDay from(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> MO;
            case TUESDAY -> TU;
            case WEDNESDAY -> WE;
            case THURSDAY -> TH;
            case FRIDAY -> FR;
            case SATURDAY -> SA;
            case SUNDAY -> SU;
        };
    }
}
