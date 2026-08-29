package pj.dooit.task.domain;

public enum TaskType {
    /**
     * 날짜/시간 기준으로 캘린더에 표시되는 일정.
     */
    SCHEDULE,

    /**
     * 완료 여부를 중심으로 관리할 할 일.
     */
    TODO,

    /**
     * 날짜가 정해지지 않아도 되는 생각, 아이디어.
     */
    IDEA;

    /**
     * 기존 요청과 데이터는 일정으로 간주한다.
     */
    public static TaskType defaultType() {
        return SCHEDULE;
    }
}
