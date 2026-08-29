package pj.dooit.batch.reader;

import pj.dooit.batch.domain.ScheduleMailSection;
import pj.dooit.batch.domain.ScheduleSectionType;
import pj.dooit.task.domain.query.TaskQueryType;
import pj.dooit.task.dto.TaskQueryRequest;
import pj.dooit.task.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.*;

class DailyScheduleMailSectionReaderTest {

    private TaskService taskService;
    private DailyScheduleMailSectionReader reader;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        reader = new DailyScheduleMailSectionReader(taskService);
        ReflectionTestUtils.setField(reader, "baseDateParam", "2026-03-12");
    }

    @Test
    @DisplayName("처음 read 호출 시 섹션을 생성하고 SEED -> TODAY -> WEEK 순서로 반환한다")
    void read_returnSectionsInOrder() {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        // when
        ScheduleMailSection first = reader.read();
        ScheduleMailSection second = reader.read();
        ScheduleMailSection third = reader.read();
        ScheduleMailSection fourth = reader.read();

        // then
        assertThat(first).isNotNull();
        assertThat(first.type()).isEqualTo(ScheduleSectionType.SEED);

        assertThat(second).isNotNull();
        assertThat(second.type()).isEqualTo(ScheduleSectionType.TODAY);

        assertThat(third).isNotNull();
        assertThat(third.type()).isEqualTo(ScheduleSectionType.WEEK);

        assertThat(fourth).isNull();
    }

    @Test
    @DisplayName("처음 read 호출 시 TaskService 를 baseDate 기준으로 조회한다")
    void read_callsTaskServiceWithBaseDate() {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        // when
        reader.read();

        // then
        then(taskService).should().getUnscheduledTasks();
        then(taskService).should(times(2)).getTasks(any(TaskQueryRequest.class));
    }

    @Test
    @DisplayName("TODAY 조회는 DAY 타입과 baseDate 문자열로 요청한다")
    void read_callsTodayQueryCorrectly() {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        // when
        reader.read();

        // then
        then(taskService).should().getTasks(
                refEq(new TaskQueryRequest(TaskQueryType.DAY, "2026-03-12"))
        );
    }

    @Test
    @DisplayName("WEEK 조회는 WEEk 타입과 baseDate 문자열로 요청한다")
    void read_callsWeekQueryCorrectly() {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        // when
        reader.read();

        // then
        then(taskService).should().getTasks(
                refEq(new TaskQueryRequest(TaskQueryType.WEEK, "2026-03-12"))
        );
    }

    @Test
    @DisplayName("delegate는 한 번만 생성되고 이후 read에서는 TaskService를 다시 호출하지 않는다")
    void read_initalizesDelegateOnlyOnce() {
        // given
        given(taskService.getUnscheduledTasks()).willReturn(List.of());
        given(taskService.getTasks(any(TaskQueryRequest.class))).willReturn(List.of());

        // when
        reader.read();
        reader.read();
        reader.read();
        reader.read();

        // then
        then(taskService).should(times(1)).getUnscheduledTasks();
        then(taskService).should(times(2)).getTasks(any(TaskQueryRequest.class));
    }
}