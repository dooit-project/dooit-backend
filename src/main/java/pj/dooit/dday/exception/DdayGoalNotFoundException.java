package pj.dooit.dday.exception;

public class DdayGoalNotFoundException extends RuntimeException {

    private final Long id;

    public DdayGoalNotFoundException(Long id) {
        super("D-Day 목표를 찾을 수 없습니다. id=" + id);
        this.id = id;
    }

    public String getDetail() {
        return "id=" + id;
    }
}
