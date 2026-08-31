package pj.dooit.task.service;

import pj.dooit.dailyplan.service.DailyPlanService;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.exception.DdayGoalNotFoundException;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.task.domain.DeferReason;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.domain.TodayOrderDirection;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.exception.TaskNotFoundException;
import pj.dooit.task.exception.TaskValidationException;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TaskTxServiceTest {

    @Mock
    TaskRepository taskRepository;

    @Mock
    DdayGoalRepository ddayGoalRepository;

    @Mock
    DailyPlanService dailyPlanService;

    @Mock
    TaskChecklistItemService checklistItemService;

    private TaskTxService service() {
        return new TaskTxService(taskRepository, ddayGoalRepository, dailyPlanService, checklistItemService);
    }

    @Test
    @DisplayName("updateTx는 날짜 없는 Today Task의 설명을 수정해도 실행 상태를 유지한다")
    void updateTx_unscheduledToday_preservesWorkflowState() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 6, 9);
        Task task = Task.builder()
                .title("기존 제목")
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .build();
        TaskRequest request = new TaskRequest(
                "기존 제목",
                "새 설명",
                TaskType.TODO,
                null,
                null,
                null,
                false
        );
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.updateTx(id, request);

        // then
        assertThat(result.getDescription()).isEqualTo("새 설명");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(result.getTargetDate()).isEqualTo(targetDate);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("updateTxForOwner는 owner 조건으로 Task를 조회한다")
    void updateTxForOwner_success_ownerScoped() {
        long id = 1L;
        User owner = persistedOwner(10L);
        Task task = Task.builder()
                .title("기존 제목")
                .owner(owner)
                .build();
        TaskRequest request = new TaskRequest(
                "새 제목",
                null,
                TaskType.TODO,
                null,
                null,
                null,
                false
        );
        TaskTxService service = service();

        given(taskRepository.findByIdAndOwnerId(id, 10L)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        Task result = service.updateTxForOwner(id, request, owner);

        assertThat(result.getTitle()).isEqualTo("새 제목");
        then(taskRepository).should().findByIdAndOwnerId(id, 10L);
        then(taskRepository).should().save(task);
    }

    @Test
    @DisplayName("updateTxForOwner는 다른 사용자 Task를 찾지 못한 것처럼 처리한다")
    void updateTxForOwner_fail_notFound() {
        long id = 1L;
        User owner = persistedOwner(10L);
        TaskRequest request = new TaskRequest(
                "새 제목",
                null,
                TaskType.TODO,
                null,
                null,
                null,
                false
        );
        TaskTxService service = service();
        given(taskRepository.findByIdAndOwnerId(id, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTxForOwner(id, request, owner))
                .isInstanceOf(TaskNotFoundException.class);

        then(taskRepository).should().findByIdAndOwnerId(id, 10L);
    }


    @Test
    @DisplayName("moveToTodayTx는 일정 없는 Task에 자동 종일 일정을 만들고 저장한다")
    void moveToTodayTx_createsAutoAllDaySchedule() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 21);
        Task task = Task.builder()
                .title("task")
                .type(TaskType.TODO)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.findMaxTodayOrder(targetDate)).willReturn(4);
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.moveToTodayTx(id, targetDate);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(result.getTargetDate()).isEqualTo(targetDate);
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getStartAt()).isEqualTo(targetDate.atStartOfDay());
        assertThat(result.getEndAt()).isEqualTo(targetDate.plusDays(1).atStartOfDay());
        assertThat(result.isAllDay()).isTrue();
        assertThat(result.getTodayOrder()).isEqualTo(5);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findMaxTodayOrder(targetDate);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("moveToTodayTx는 기존 수동 일정의 시간을 유지하고 선택 날짜로 이동한다")
    void moveToTodayTx_movesUserSchedulePreservingTime() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 21);
        LocalDateTime startAt = LocalDateTime.of(2026, 5, 20, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 5, 20, 11, 0);
        Task task = Task.builder()
                .title("task")
                .startAt(startAt)
                .endAt(endAt)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.findMaxTodayOrder(targetDate)).willReturn(null);
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.moveToTodayTx(id, targetDate);

        // then
        assertThat(result.getTargetDate()).isEqualTo(targetDate);
        assertThat(result.getStartAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 10, 0));
        assertThat(result.getEndAt()).isEqualTo(LocalDateTime.of(2026, 5, 21, 11, 0));
        assertThat(result.getTodayOrder()).isZero();

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findMaxTodayOrder(targetDate);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("moveToInboxTx는 자동 종일 일정을 제거하고 기록함으로 이동한다")
    void moveToInboxTx_removesAutoTodaySchedule() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 6, 11);
        Task task = Task.builder()
                .title("task")
                .type(TaskType.TODO)
                .startAt(targetDate.atStartOfDay())
                .endAt(targetDate.plusDays(1).atStartOfDay())
                .allDay(true)
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.moveToInboxTx(id);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.INBOX);
        assertThat(result.getTargetDate()).isNull();
        assertThat(result.getStartAt()).isNull();

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("moveToInboxTx는 사용자 지정 일정도 제거한다")
    void moveToInboxTx_removesUserSchedule() {
        // given
        long id = 1L;
        Task task = Task.builder()
                .title("task")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 6, 11, 14, 0))
                .endAt(LocalDateTime.of(2026, 6, 11, 15, 0))
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 6, 11))
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.moveToInboxTx(id);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.INBOX);
        assertThat(result.getStartAt()).isNull();
        assertThat(result.getEndAt()).isNull();
        assertThat(result.getType()).isEqualTo(TaskType.TODO);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("completeTx는 Task를 Done 상태로 변경하고 저장한다")
    void completeTx_success() {
        // given
        long id = 1L;
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 21, 22, 0);
        Task task = Task.builder()
                .title("task")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 21))
                .carryOverCount(1)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.completeTx(id, completedAt);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(result.getCompletedAt()).isEqualTo(completedAt);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("reopenTodayTx는 Done Task를 지정 날짜의 자동 종일 일정으로 되돌리고 저장한다")
    void reopenTodayTx_success() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 21);
        Task task = Task.builder()
                .title("task")
                .status(TaskStatus.DONE)
                .completedAt(LocalDateTime.of(2026, 5, 21, 22, 0))
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.findMaxTodayOrder(targetDate)).willReturn(2);
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.reopenTodayTx(id, targetDate);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(result.getTargetDate()).isEqualTo(targetDate);
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getStartAt()).isEqualTo(targetDate.atStartOfDay());
        assertThat(result.getEndAt()).isEqualTo(targetDate.plusDays(1).atStartOfDay());
        assertThat(result.isAllDay()).isTrue();
        assertThat(result.getTodayOrder()).isEqualTo(3);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findMaxTodayOrder(targetDate);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("carryOverTx는 자동 종일 일정과 실행일을 함께 다음 날짜로 이동한다")
    void carryOverTx_movesAutoTodaySchedule() {
        // given
        long id = 1L;
        LocalDate currentDate = LocalDate.of(2026, 5, 21);
        LocalDate nextDate = LocalDate.of(2026, 5, 22);
        Task task = Task.builder()
                .title("task")
                .startAt(currentDate.atStartOfDay())
                .endAt(currentDate.plusDays(1).atStartOfDay())
                .allDay(true)
                .status(TaskStatus.TODAY)
                .targetDate(currentDate)
                .carryOverCount(1)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.findMaxTodayOrder(nextDate)).willReturn(7);
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.carryOverTx(id, nextDate);

        // then
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(result.getTargetDate()).isEqualTo(nextDate);
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getCarryOverCount()).isEqualTo(2);
        assertThat(result.getStartAt()).isEqualTo(nextDate.atStartOfDay());
        assertThat(result.getEndAt()).isEqualTo(nextDate.plusDays(1).atStartOfDay());
        assertThat(result.getTodayOrder()).isEqualTo(8);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findMaxTodayOrder(nextDate);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("carryOverTx는 사용자 지정 일정의 시간을 유지하고 다음 날짜로 이동한다")
    void carryOverTx_movesUserSchedulePreservingTime() {
        // given
        long id = 1L;
        LocalDate nextDate = LocalDate.of(2026, 5, 22);
        LocalDateTime startAt = LocalDateTime.of(2026, 5, 21, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 5, 21, 11, 0);
        Task task = Task.builder()
                .title("task")
                .startAt(startAt)
                .endAt(endAt)
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 21))
                .carryOverCount(1)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.findMaxTodayOrder(nextDate)).willReturn(null);
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.carryOverTx(id, nextDate);

        // then
        assertThat(result.getTargetDate()).isEqualTo(nextDate);
        assertThat(result.getStartAt()).isEqualTo(LocalDateTime.of(2026, 5, 22, 10, 0));
        assertThat(result.getEndAt()).isEqualTo(LocalDateTime.of(2026, 5, 22, 11, 0));
        assertThat(result.getTodayOrder()).isZero();

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findMaxTodayOrder(nextDate);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("reorderTodayTx는 같은 날짜 Today 목록에서 인접 항목과 실행 순서를 교환한다")
    void reorderTodayTx_swapsTodayOrderWithNeighbor() {
        // given
        long id = 2L;
        LocalDate targetDate = LocalDate.of(2026, 5, 22);
        Task first = orderedTodayTask(1L, "first", targetDate, 0);
        Task second = orderedTodayTask(2L, "second", targetDate, 1);
        Task third = orderedTodayTask(3L, "third", targetDate, 2);
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(second));
        given(taskRepository.findPlannedTasks(targetDate, targetDate.plusDays(1)))
                .willReturn(List.of(first, second, third));

        // when
        Task result = service.reorderTodayTx(id, targetDate, TodayOrderDirection.DOWN);

        // then
        assertThat(result).isSameAs(second);
        assertThat(first.getTodayOrder()).isZero();
        assertThat(second.getTodayOrder()).isEqualTo(2);
        assertThat(third.getTodayOrder()).isEqualTo(1);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).findPlannedTasks(targetDate, targetDate.plusDays(1));
        then(taskRepository).should(times(1)).saveAll(List.of(first, second, third));
    }

    @Test
    @DisplayName("reorderTodayTx는 해당 날짜 Today Task가 아니면 예외가 발생한다")
    void reorderTodayTx_rejectsOtherDate() {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 22);
        Task task = orderedTodayTask(id, "task", targetDate.minusDays(1), 0);
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));

        // when & then
        assertThatThrownBy(() -> service.reorderTodayTx(id, targetDate, TodayOrderDirection.UP))
                .isInstanceOf(TaskValidationException.class);

        then(taskRepository).should(times(1)).findById(id);
    }

    @Test
    @DisplayName("상태 변경 대상 Task가 없으면 TaskNotFoundException이 발생한다")
    void changeStatus_notFound() {
        // given
        long id = 999L;
        TaskTxService service = service();
        given(taskRepository.findById(id)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.moveToTodayTx(id, LocalDate.of(2026, 5, 21)))
                .isInstanceOf(TaskNotFoundException.class);

        then(taskRepository).should(times(1)).findById(id);
    }

    @Test
    @DisplayName("connectDdayGoalTx는 Task에 D-Day 목표를 연결하고 저장한다")
    void connectDdayGoalTx_success() {
        // given
        long id = 1L;
        long ddayGoalId = 10L;
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .build();
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10));
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(ddayGoalRepository.findById(ddayGoalId)).willReturn(Optional.of(goal));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.connectDdayGoalTx(id, ddayGoalId);

        // then
        assertThat(result.getDdayGoal()).isEqualTo(goal);

        then(taskRepository).should(times(1)).findById(id);
        then(ddayGoalRepository).should(times(1)).findById(ddayGoalId);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("connectDdayGoalTxForOwner는 Task와 D-Day 목표 모두 owner 조건으로 조회한다")
    void connectDdayGoalTxForOwner_success_ownerScoped() {
        long id = 1L;
        long ddayGoalId = 2L;
        User owner = persistedOwner(10L);
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .owner(owner)
                .build();
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10), owner);
        TaskTxService service = service();

        given(taskRepository.findByIdAndOwnerId(id, 10L)).willReturn(Optional.of(task));
        given(ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, 10L)).willReturn(Optional.of(goal));
        given(taskRepository.save(task)).willReturn(task);

        Task result = service.connectDdayGoalTxForOwner(id, ddayGoalId, owner);

        assertThat(result.getDdayGoal()).isSameAs(goal);
        then(taskRepository).should().findByIdAndOwnerId(id, 10L);
        then(ddayGoalRepository).should().findByIdAndOwnerId(ddayGoalId, 10L);
        then(taskRepository).should().save(task);
    }

    @Test
    @DisplayName("connectDdayGoalTxForOwner는 다른 사용자 D-Day 목표를 찾지 못한 것처럼 처리한다")
    void connectDdayGoalTxForOwner_fail_goalNotFound() {
        long id = 1L;
        long ddayGoalId = 2L;
        User owner = persistedOwner(10L);
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .owner(owner)
                .build();
        TaskTxService service = service();

        given(taskRepository.findByIdAndOwnerId(id, 10L)).willReturn(Optional.of(task));
        given(ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.connectDdayGoalTxForOwner(id, ddayGoalId, owner))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(taskRepository).should().findByIdAndOwnerId(id, 10L);
        then(ddayGoalRepository).should().findByIdAndOwnerId(ddayGoalId, 10L);
    }

    @Test
    @DisplayName("createTodayTaskForDdayGoalTxForOwner는 목표 연결과 Today 이동을 하나의 트랜잭션에서 처리한다")
    void createTodayTaskForDdayGoalTxForOwner_success() {
        long ddayGoalId = 2L;
        User owner = persistedOwner(10L);
        LocalDate targetDate = LocalDate.of(2026, 7, 13);
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 8, 1), owner);
        TaskTxService service = service();

        given(ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, 10L)).willReturn(Optional.of(goal));
        given(taskRepository.findMaxTodayOrder(10L, targetDate)).willReturn(2);
        given(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Task result = service.createTodayTaskForDdayGoalTxForOwner(
                ddayGoalId,
                "기출 20문제 풀기",
                targetDate,
                owner
        );

        assertThat(result.getTitle()).isEqualTo("기출 20문제 풀기");
        assertThat(result.getOwner()).isSameAs(owner);
        assertThat(result.getDdayGoal()).isSameAs(goal);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.TODAY);
        assertThat(result.getTargetDate()).isEqualTo(targetDate);
        assertThat(result.getTodayOrder()).isEqualTo(3);
        assertThat(result.getStartAt()).isEqualTo(targetDate.atStartOfDay());
        assertThat(result.getEndAt()).isEqualTo(targetDate.plusDays(1).atStartOfDay());

        then(ddayGoalRepository).should().findByIdAndOwnerId(ddayGoalId, 10L);
        then(taskRepository).should().findMaxTodayOrder(10L, targetDate);
        then(taskRepository).should().save(org.mockito.ArgumentMatchers.any(Task.class));
    }

    @Test
    @DisplayName("createTodayTaskForDdayGoalTxForOwner는 다른 사용자 목표를 찾지 못한 것처럼 처리한다")
    void createTodayTaskForDdayGoalTxForOwner_fail_goalNotFound() {
        long ddayGoalId = 2L;
        User owner = persistedOwner(10L);
        LocalDate targetDate = LocalDate.of(2026, 7, 13);
        TaskTxService service = service();

        given(ddayGoalRepository.findByIdAndOwnerId(ddayGoalId, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTodayTaskForDdayGoalTxForOwner(
                ddayGoalId,
                "기출 20문제 풀기",
                targetDate,
                owner
        ))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(ddayGoalRepository).should().findByIdAndOwnerId(ddayGoalId, 10L);
    }

    @Test
    @DisplayName("connectDdayGoalTx는 D-Day 목표가 없으면 DdayGoalNotFoundException이 발생한다")
    void connectDdayGoalTx_fail_goalNotFound() {
        // given
        long id = 1L;
        long ddayGoalId = 99L;
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(ddayGoalRepository.findById(ddayGoalId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.connectDdayGoalTx(id, ddayGoalId))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(taskRepository).should(times(1)).findById(id);
        then(ddayGoalRepository).should(times(1)).findById(ddayGoalId);
    }

    @Test
    @DisplayName("disconnectDdayGoalTx는 Task의 D-Day 목표 연결을 해제하고 저장한다")
    void disconnectDdayGoalTx_success() {
        // given
        long id = 1L;
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10));
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .ddayGoal(goal)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.disconnectDdayGoalTx(id);

        // then
        assertThat(result.getDdayGoal()).isNull();

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("setDeferReasonTx는 Task에 미룬 이유를 저장한다")
    void setDeferReasonTx_success() {
        // given
        long id = 1L;
        Task task = Task.builder()
                .title("task")
                .carryOverCount(3)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.setDeferReasonTx(id, DeferReason.TOO_BIG);

        // then
        assertThat(result.getDeferReason()).isEqualTo(DeferReason.TOO_BIG);

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    @Test
    @DisplayName("clearDeferReasonTx는 Task의 미룬 이유를 해제한다")
    void clearDeferReasonTx_success() {
        // given
        long id = 1L;
        Task task = Task.builder()
                .title("task")
                .deferReason(DeferReason.TOO_BIG)
                .build();
        TaskTxService service = service();

        given(taskRepository.findById(id)).willReturn(Optional.of(task));
        given(taskRepository.save(task)).willReturn(task);

        // when
        Task result = service.clearDeferReasonTx(id);

        // then
        assertThat(result.getDeferReason()).isNull();

        then(taskRepository).should(times(1)).findById(id);
        then(taskRepository).should(times(1)).save(task);
    }

    private Task orderedTodayTask(Long id, String title, LocalDate targetDate, int todayOrder) {
        Task task = Task.builder()
                .title(title)
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .todayOrder(todayOrder)
                .build();
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private User persistedOwner(Long id) {
        User owner = new User("owner" + id + "@example.com", "encoded-password", "Owner");
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }
}
