package com.todolab.workspace.controller;

import com.todolab.auth.service.JwtTokenService;
import com.todolab.dday.domain.DdayGoal;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.mail.MailService;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskType;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import com.todolab.workspace.domain.SharedWorkspace;
import com.todolab.workspace.repository.SharedWorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceScopeIsolationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    SharedWorkspaceRepository sharedWorkspaceRepository;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("개인 Task API는 같은 owner의 workspace Task를 반환하지 않는다")
    void personalTaskApi_excludesWorkspaceTasks() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-task@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace("공유 공간", null, owner));

        taskRepository.save(Task.builder()
                .title("개인 일정")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 8, 13, 9, 0))
                .endAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .owner(owner)
                .build());
        Task workspaceTask = Task.builder()
                .title("공유 일정")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 8, 13, 11, 0))
                .endAt(LocalDateTime.of(2026, 8, 13, 12, 0))
                .owner(owner)
                .build();
        workspaceTask.assignWorkspace(workspace);
        workspaceTask = taskRepository.save(workspaceTask);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "DAY")
                        .param("date", "2026-08-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("개인 일정"));

        mockMvc.perform(get("/api/v1/tasks/{id}", workspaceTask.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001));
    }

    @Test
    @DisplayName("개인 D-Day API는 같은 owner의 workspace D-Day를 반환하지 않는다")
    void personalDdayApi_excludesWorkspaceDdays() throws Exception {
        User owner = userRepository.save(new User("workspace-scope-dday@example.com", "encoded-password", "Scope 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        SharedWorkspace workspace = sharedWorkspaceRepository.save(new SharedWorkspace("공유 공간", null, owner));

        ddayGoalRepository.save(new DdayGoal("개인 목표", LocalDate.of(2026, 8, 31), owner));
        DdayGoal workspaceGoal = new DdayGoal("공유 목표", LocalDate.of(2026, 9, 30), owner);
        workspaceGoal.assignWorkspace(workspace);
        workspaceGoal = ddayGoalRepository.save(workspaceGoal);

        mockMvc.perform(get("/api/v1/dday-goals")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("개인 목표"));

        mockMvc.perform(get("/api/v1/dday-goals/{id}", workspaceGoal.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(30001));
    }
}
