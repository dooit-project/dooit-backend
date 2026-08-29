package pj.dooit.batch.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduleSectionType {
    SEED(1, "씨드"),
    TODAY(2, "오늘 일정"),
    WEEK(3, "이번 주 일정");

    private final int order;
    private final String title;
}
