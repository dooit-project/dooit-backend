package com.todolab.task.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.Task;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskResponse;
import com.todolab.task.service.TaskService;
import com.todolab.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TaskV1ControllerTest {

    @Mock
    TaskService taskService;

    @Mock
    CurrentUserService currentUserService;

    @Test
    @DisplayName("v1 Task 생성은 JWT 사용자를 owner로 전달한다")
    void createTask_success_ownerAware() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = new User("owner@example.com", "encoded-password", "Owner");
        TaskRequest request = new TaskRequest("owned task", null, null, null, null, false);
        TaskResponse response = TaskResponse.from(Task.builder()
                .title("owned task")
                .owner(owner)
                .build());
        given(currentUserService.requireUser(jwt)).willReturn(owner);
        given(taskService.createForOwner(request, owner)).willReturn(response);

        var result = controller.createTask(jwt, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().title()).isEqualTo("owned task");
        then(currentUserService).should().requireUser(jwt);
        then(taskService).should().createForOwner(request, owner);
    }

    @Test
    @DisplayName("v1 Task 단건 조회는 owner 범위 서비스를 호출한다")
    void getTask_success_ownerScoped() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = owner();
        TaskResponse response = TaskResponse.from(Task.builder().title("owned task").owner(owner).build());
        given(currentUserService.requireUser(jwt)).willReturn(owner);
        given(taskService.getTaskForOwner(10L, owner)).willReturn(response);

        var result = controller.getTask(jwt, 10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().title()).isEqualTo("owned task");
        then(taskService).should().getTaskForOwner(10L, owner);
    }

    @Test
    @DisplayName("v1 Today 조회는 owner 범위 서비스를 호출한다")
    void getTodayTasks_success_ownerScoped() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = owner();
        LocalDate date = LocalDate.of(2026, 7, 13);
        TaskResponse response = TaskResponse.from(Task.builder()
                .title("today")
                .status(TaskStatus.TODAY)
                .targetDate(date)
                .owner(owner)
                .build());
        given(currentUserService.requireUser(jwt)).willReturn(owner);
        given(taskService.getTodayTasksForOwner(date, owner)).willReturn(List.of(response));

        var result = controller.getTodayTasks(jwt, date);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).hasSize(1);
        then(taskService).should().getTodayTasksForOwner(date, owner);
    }

    @Test
    @DisplayName("v1 Task 수정은 owner 범위 서비스를 호출한다")
    void updateTask_success_ownerScoped() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = owner();
        TaskRequest request = new TaskRequest("updated", null, null, null, null, false);
        TaskResponse response = TaskResponse.from(Task.builder().title("updated").owner(owner).build());
        given(currentUserService.requireUser(jwt)).willReturn(owner);
        given(taskService.updateForOwner(10L, request, owner, null)).willReturn(response);

        var result = controller.updateTask(jwt, 10L, request, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data().title()).isEqualTo("updated");
        then(taskService).should().updateForOwner(10L, request, owner, null);
    }

    @Test
    @DisplayName("v1 D-Day 연결은 owner 범위 서비스를 호출한다")
    void connectDdayGoal_success_ownerScoped() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = owner();
        TaskResponse response = TaskResponse.from(Task.builder().title("connected").owner(owner).build());
        given(currentUserService.requireUser(jwt)).willReturn(owner);
        given(taskService.connectDdayGoalForOwner(10L, 2L, owner)).willReturn(response);

        var result = controller.connectDdayGoal(jwt, 10L, 2L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        then(taskService).should().connectDdayGoalForOwner(10L, 2L, owner);
    }

    @Test
    @DisplayName("v1 Task 삭제는 owner 범위 서비스를 호출하고 null data를 반환한다")
    void deleteTask_success_ownerScoped() {
        TaskV1Controller controller = new TaskV1Controller(taskService, currentUserService);
        Jwt jwt = jwt("1");
        User owner = owner();
        given(currentUserService.requireUser(jwt)).willReturn(owner);

        var result = controller.deleteTask(jwt, 10L, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isNull();
        then(taskService).should().deleteForOwner(10L, owner, null);
    }

    private Jwt jwt(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    private User owner() {
        return new User("owner@example.com", "encoded-password", "Owner");
    }
}
