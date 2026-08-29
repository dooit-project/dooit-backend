package pj.dooit.dday.service;

import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.dto.DdayGoalRequest;
import pj.dooit.dday.exception.DdayGoalNotFoundException;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.task.domain.Task;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class DdayGoalServiceTest {

    @Mock
    DdayGoalRepository ddayGoalRepository;

    @Mock
    TaskRepository taskRepository;

    @Test
    @DisplayName("D-Day 목표를 생성한다")
    void create_success() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10));
        given(ddayGoalRepository.save(org.mockito.ArgumentMatchers.any(DdayGoal.class))).willReturn(goal);

        var response = service.create(new DdayGoalRequest("정보처리기사", LocalDate.of(2026, 6, 10)));

        assertThat(response.title()).isEqualTo("정보처리기사");
        assertThat(response.targetDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        then(ddayGoalRepository).should().save(org.mockito.ArgumentMatchers.any(DdayGoal.class));
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 생성은 owner를 저장한다")
    void createForOwner_success_assignOwner() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        User owner = new User("owner@example.com", "encoded-password", "Owner");
        given(ddayGoalRepository.save(org.mockito.ArgumentMatchers.any(DdayGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        var response = service.createForOwner(
                new DdayGoalRequest("정보처리기사", LocalDate.of(2026, 6, 10)),
                owner
        );

        assertThat(response.title()).isEqualTo("정보처리기사");
        ArgumentCaptor<DdayGoal> goalCaptor = ArgumentCaptor.forClass(DdayGoal.class);
        then(ddayGoalRepository).should().save(goalCaptor.capture());
        assertThat(goalCaptor.getValue().getOwner()).isSameAs(owner);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 생성은 owner가 필수다")
    void createForOwner_fail_nullOwner() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);

        assertThatThrownBy(() -> service.createForOwner(
                new DdayGoalRequest("정보처리기사", LocalDate.of(2026, 6, 10)),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("owner는 필수입니다.");

        then(ddayGoalRepository).shouldHaveNoInteractions();
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("D-Day 목표를 날짜순으로 조회한다")
    void findAll_success() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        given(ddayGoalRepository.findAllByOrderByTargetDateAscIdAsc()).willReturn(List.of(
                new DdayGoal("포트폴리오 제출", LocalDate.of(2026, 6, 5))
        ));

        var responses = service.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("포트폴리오 제출");
        then(ddayGoalRepository).should().findAllByOrderByTargetDateAscIdAsc();
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 목록 조회는 owner 조건을 적용한다")
    void findAllForOwner_success_ownerScoped() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        User owner = persistedOwner(10L);
        given(ddayGoalRepository.findAllByOwnerIdOrderByTargetDateAscIdAsc(10L)).willReturn(List.of(
                new DdayGoal("포트폴리오 제출", LocalDate.of(2026, 6, 5), owner)
        ));

        var responses = service.findAllForOwner(owner);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("포트폴리오 제출");
        then(ddayGoalRepository).should().findAllByOwnerIdOrderByTargetDateAscIdAsc(10L);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 단건 조회는 owner 조건을 적용한다")
    void getForOwner_success_ownerScoped() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        User owner = persistedOwner(10L);
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10), owner);
        given(ddayGoalRepository.findByIdAndOwnerId(1L, 10L)).willReturn(java.util.Optional.of(goal));

        var response = service.getForOwner(1L, owner);

        assertThat(response.title()).isEqualTo("정보처리기사");
        then(ddayGoalRepository).should().findByIdAndOwnerId(1L, 10L);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 단건 조회는 다른 사용자 목표를 찾지 못한 것처럼 처리한다")
    void getForOwner_fail_notFound() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        User owner = persistedOwner(10L);
        given(ddayGoalRepository.findByIdAndOwnerId(99L, 10L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getForOwner(99L, owner))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(ddayGoalRepository).should().findByIdAndOwnerId(99L, 10L);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("D-Day 목표를 날짜 범위로 조회한다")
    void findByDateRange_success() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);
        given(ddayGoalRepository.findByTargetDateBetweenOrderByTargetDateAscIdAsc(startDate, endDate))
                .willReturn(List.of(new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10))));

        var responses = service.findByDateRange(startDate, endDate);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("정보처리기사");
        assertThat(responses.getFirst().targetDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        then(ddayGoalRepository).should().findByTargetDateBetweenOrderByTargetDateAscIdAsc(startDate, endDate);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("D-Day 목표에 연결된 Task를 조회한다")
    void findTasks_success() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        long ddayGoalId = 1L;
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10));
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 30))
                .ddayGoal(goal)
                .build();

        given(ddayGoalRepository.existsById(ddayGoalId)).willReturn(true);
        given(taskRepository.findByDdayGoalId(ddayGoalId)).willReturn(List.of(task));

        var responses = service.findTasks(ddayGoalId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("기출 20문제 풀기");
        assertThat(responses.getFirst().status()).isEqualTo(TaskStatus.TODAY);
        then(ddayGoalRepository).should().existsById(ddayGoalId);
        then(taskRepository).should().findByDdayGoalId(ddayGoalId);
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 연결 Task 조회는 목표와 Task 모두 owner 조건을 적용한다")
    void findTasksForOwner_success_ownerScoped() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        long ddayGoalId = 1L;
        User owner = persistedOwner(10L);
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10), owner);
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 30))
                .ddayGoal(goal)
                .owner(owner)
                .build();

        given(ddayGoalRepository.existsByIdAndOwnerId(ddayGoalId, 10L)).willReturn(true);
        given(taskRepository.findByDdayGoalId(10L, ddayGoalId)).willReturn(List.of(task));

        var responses = service.findTasksForOwner(ddayGoalId, owner);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().title()).isEqualTo("기출 20문제 풀기");
        then(ddayGoalRepository).should().existsByIdAndOwnerId(ddayGoalId, 10L);
        then(taskRepository).should().findByDdayGoalId(10L, ddayGoalId);
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 연결 Task 조회는 다른 사용자 목표를 찾지 못한 것처럼 처리한다")
    void findTasksForOwner_fail_notFound() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        User owner = persistedOwner(10L);
        given(ddayGoalRepository.existsByIdAndOwnerId(99L, 10L)).willReturn(false);

        assertThatThrownBy(() -> service.findTasksForOwner(99L, owner))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(ddayGoalRepository).should().existsByIdAndOwnerId(99L, 10L);
        then(taskRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 D-Day 목표의 Task 조회 시 예외를 던진다")
    void findTasks_fail_notFound() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        given(ddayGoalRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.findTasks(99L))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(ddayGoalRepository).should().existsById(99L);
        then(taskRepository).should(never()).findByDdayGoalId(99L);
    }

    @Test
    @DisplayName("D-Day 목표 삭제 시 연결된 Task는 보존하고 연결만 해제한다")
    void delete_success_disconnectTasks() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        long ddayGoalId = 1L;
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10));
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 31))
                .ddayGoal(goal)
                .build();

        given(ddayGoalRepository.existsById(ddayGoalId)).willReturn(true);
        given(taskRepository.findByDdayGoalId(ddayGoalId)).willReturn(List.of(task));

        service.delete(ddayGoalId);

        assertThat(task.getDdayGoal()).isNull();
        then(ddayGoalRepository).should().existsById(ddayGoalId);
        then(taskRepository).should().findByDdayGoalId(ddayGoalId);
        then(ddayGoalRepository).should().deleteById(ddayGoalId);
    }

    @Test
    @DisplayName("인증 사용자용 D-Day 목표 삭제는 owner 조건을 적용한다")
    void deleteForOwner_success_ownerScoped() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        long ddayGoalId = 1L;
        User owner = persistedOwner(10L);
        DdayGoal goal = new DdayGoal("정보처리기사", LocalDate.of(2026, 6, 10), owner);
        Task task = Task.builder()
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 31))
                .ddayGoal(goal)
                .owner(owner)
                .build();

        given(ddayGoalRepository.existsByIdAndOwnerId(ddayGoalId, 10L)).willReturn(true);
        given(taskRepository.findByDdayGoalId(10L, ddayGoalId)).willReturn(List.of(task));

        service.deleteForOwner(ddayGoalId, owner);

        assertThat(task.getDdayGoal()).isNull();
        then(ddayGoalRepository).should().existsByIdAndOwnerId(ddayGoalId, 10L);
        then(taskRepository).should().findByDdayGoalId(10L, ddayGoalId);
        then(ddayGoalRepository).should().deleteById(ddayGoalId);
    }

    @Test
    @DisplayName("존재하지 않는 D-Day 목표 삭제 시 예외를 던진다")
    void delete_fail_notFound() {
        DdayGoalService service = new DdayGoalService(ddayGoalRepository, taskRepository);
        given(ddayGoalRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(DdayGoalNotFoundException.class);

        then(ddayGoalRepository).should().existsById(99L);
        then(taskRepository).shouldHaveNoInteractions();
    }

    private User persistedOwner(Long id) {
        User owner = new User("owner" + id + "@example.com", "encoded-password", "Owner");
        ReflectionTestUtils.setField(owner, "id", id);
        return owner;
    }
}
