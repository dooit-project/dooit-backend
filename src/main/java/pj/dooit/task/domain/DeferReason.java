package pj.dooit.task.domain;

public enum DeferReason {
    TOO_BIG("너무 큼"),
    NOT_NEEDED_NOW("지금 필요 없음"),
    AVOIDING("하기 싫음"),
    NO_DEADLINE("마감 없음"),
    WAITING_OTHER("다른 사람 대기"),
    ETC("기타");

    private final String label;

    DeferReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
