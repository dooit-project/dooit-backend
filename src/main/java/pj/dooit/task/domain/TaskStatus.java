package pj.dooit.task.domain;

public enum TaskStatus {
    /**
     * 빠르게 기록했지만 아직 오늘 할 일로 고르지 않은 항목.
     */
    INBOX,

    /**
     * 특정 날짜에 실행하기로 고른 항목.
     */
    TODAY,

    /**
     * 완료한 항목.
     */
    DONE
}
