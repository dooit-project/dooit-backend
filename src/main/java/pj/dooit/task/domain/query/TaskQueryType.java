package pj.dooit.task.domain.query;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public enum TaskQueryType {

    DAY(DateRange::ofDay),
    WEEK(DateRange::ofWeek),
    MONTH(DateRange::ofMonth);

    private final Function<String, DateRange> calculator;

    public static TaskQueryType from(String value) {
        try {
            return TaskQueryType.valueOf(value.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("지원하지 않는 조회 타입: " + value);
        }
    }

    public DateRange calculate(String date) {
        return calculator.apply(date);
    }
}
