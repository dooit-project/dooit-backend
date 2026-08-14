package com.todolab.workspace.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.dday.dto.DdayGoalRequest;
import com.todolab.mail.MailService;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.TaskType;
import com.todolab.task.dto.TaskRecurrenceRequest;
import com.todolab.task.dto.TaskRequest;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import com.todolab.workspace.domain.WorkspaceMemberStatus;
import com.todolab.workspace.domain.WorkspaceRole;
import com.todolab.workspace.dto.WorkspaceInviteRequest;
import com.todolab.workspace.dto.WorkspaceMemberUpdateRequest;
import com.todolab.workspace.dto.WorkspaceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceTaskV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 Workspace Task는 OWNER가 생성하고 VIEWER가 조회한다")
    void createAndReadWorkspaceTask_success() throws Exception {
        String ownerToken = accessToken("workspace-task-owner@example.com");
        String viewerToken = accessToken("workspace-task-viewer@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);

        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 회의",
                "킥오프",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 17, 9, 0),
                LocalDateTime.of(2026, 8, 17, 10, 0),
                "업무",
                false
        ));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("type", "DAY")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(taskId))
                .andExpect(jsonPath("$.data[0].title").value("공유 회의"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.title").value("공유 회의"));
    }

    @Test
    @DisplayName("v1 Workspace Task 생성은 VIEWER에게 403을 반환한다")
    void createWorkspaceTask_forbiddenForViewer() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-forbidden@example.com");
        String viewerToken = accessToken("workspace-task-viewer-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer-forbidden@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "공유 회의",
                                null,
                                TaskType.SCHEDULE,
                                LocalDateTime.of(2026, 8, 17, 9, 0),
                                LocalDateTime.of(2026, 8, 17, 10, 0),
                                null,
                                false
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace Task는 EDITOR가 수정하고 VIEWER가 변경 내용을 조회한다")
    void updateWorkspaceTask_successForEditor() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-update@example.com");
        String editorToken = accessToken("workspace-task-editor-update@example.com");
        String viewerToken = accessToken("workspace-task-viewer-update@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-task-editor-update@example.com", WorkspaceRole.EDITOR);
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer-update@example.com", WorkspaceRole.VIEWER);
        accept(editorToken, workspaceId, editorMemberId);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 회의",
                "킥오프",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 17, 9, 0),
                LocalDateTime.of(2026, 8, 17, 10, 0),
                "업무",
                false
        ));

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "공유 회의 변경",
                                "회의실 확정",
                                TaskType.SCHEDULE,
                                LocalDateTime.of(2026, 8, 17, 10, 0),
                                LocalDateTime.of(2026, 8, 17, 11, 0),
                                "제품",
                                false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.title").value("공유 회의 변경"))
                .andExpect(jsonPath("$.data.category").value("제품"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("공유 회의 변경"))
                .andExpect(jsonPath("$.data.description").value("회의실 확정"));
    }

    @Test
    @DisplayName("v1 Workspace Task 수정과 삭제는 VIEWER에게 403을 반환한다")
    void mutateWorkspaceTask_forbiddenForViewer() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-mutate-forbidden@example.com");
        String viewerToken = accessToken("workspace-task-viewer-mutate-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer-mutate-forbidden@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 회의",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 17, 9, 0),
                LocalDateTime.of(2026, 8, 17, 10, 0),
                null,
                false
        ));

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "공유 회의 변경",
                                null,
                                TaskType.SCHEDULE,
                                LocalDateTime.of(2026, 8, 17, 10, 0),
                                LocalDateTime.of(2026, 8, 17, 11, 0),
                                null,
                                false
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace Task는 EDITOR가 삭제한다")
    void deleteWorkspaceTask_successForEditor() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-delete@example.com");
        String editorToken = accessToken("workspace-task-editor-delete@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-task-editor-delete@example.com", WorkspaceRole.EDITOR);
        accept(editorToken, workspaceId, editorMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 회의",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 17, 9, 0),
                LocalDateTime.of(2026, 8, 17, 10, 0),
                null,
                false
        ));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001));
    }

    @Test
    @DisplayName("v1 Workspace Task는 같은 workspace D-Day에 연결하고 해제한다")
    void connectAndDisconnectWorkspaceDdayGoal_success() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-dday@example.com");
        String editorToken = accessToken("workspace-task-editor-dday@example.com");
        String viewerToken = accessToken("workspace-task-viewer-dday@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-task-editor-dday@example.com", WorkspaceRole.EDITOR);
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer-dday@example.com", WorkspaceRole.VIEWER);
        accept(editorToken, workspaceId, editorMemberId);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 출시 준비",
                null,
                TaskType.TODO,
                null,
                null,
                null,
                false
        ));
        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal", workspaceId, taskId)
                        .header("Authorization", "Bearer " + editorToken)
                        .param("ddayGoalId", goalId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.ddayGoalId").value(goalId))
                .andExpect(jsonPath("$.data.ddayGoalTitle").value("공유 출시"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}/tasks", workspaceId, goalId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(taskId));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal", workspaceId, taskId)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ddayGoalId").isEmpty());
    }

    @Test
    @DisplayName("v1 Workspace Task는 다른 workspace D-Day에 연결할 수 없다")
    void connectWorkspaceDdayGoal_notFoundForOtherWorkspaceGoal() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-other-dday@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long otherWorkspaceId = createWorkspace(ownerToken, "마케팅팀 일정");
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 출시 준비",
                null,
                TaskType.TODO,
                null,
                null,
                null,
                false
        ));
        Long otherGoalId = createWorkspaceDday(ownerToken, otherWorkspaceId, new DdayGoalRequest(
                "다른 팀 출시",
                LocalDate.of(2026, 10, 31)
        ));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal", workspaceId, taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("ddayGoalId", otherGoalId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(30001));
    }

    @Test
    @DisplayName("v1 Workspace Task D-Day 연결은 VIEWER에게 403을 반환한다")
    void connectWorkspaceDdayGoal_forbiddenForViewer() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-dday-forbidden@example.com");
        String viewerToken = accessToken("workspace-task-viewer-dday-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-task-viewer-dday-forbidden@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 출시 준비",
                null,
                TaskType.TODO,
                null,
                null,
                null,
                false
        ));
        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/tasks/{taskId}/dday-goal", workspaceId, taskId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .param("ddayGoalId", goalId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace Task 조회는 non-member에게 404를 반환한다")
    void readWorkspaceTask_notFoundForNonMember() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-nonmember@example.com");
        String outsiderToken = accessToken("workspace-task-outsider@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, new TaskRequest(
                "공유 회의",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 17, 9, 0),
                LocalDateTime.of(2026, 8, 17, 10, 0),
                null,
                false
        ));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/tasks/{taskId}", workspaceId, taskId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(50001));
    }

    @Test
    @DisplayName("v1 Workspace 반복 Task 생성은 아직 거부한다")
    void createWorkspaceRecurringTask_rejected() throws Exception {
        String ownerToken = accessToken("workspace-task-owner-recurrence@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "공유 반복 회의",
                                null,
                                TaskType.SCHEDULE,
                                LocalDateTime.of(2026, 8, 17, 9, 0),
                                LocalDateTime.of(2026, 8, 17, 10, 0),
                                null,
                                false,
                                new TaskRecurrenceRequest(
                                        RecurrenceFrequency.WEEKLY,
                                        1,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of("MO"),
                                        null
                                )
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    private String accessToken(String email) {
        User user = userRepository.save(new User(email, "encoded-password", "Workspace Task 사용자"));
        return jwtTokenService.createAccessToken(user).tokenValue();
    }

    private Long createWorkspace(String accessToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceRequest(name, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long createWorkspaceTask(String accessToken, Long workspaceId, TaskRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long createWorkspaceDday(String accessToken, Long workspaceId, DdayGoalRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/dday-goals", workspaceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long invite(String ownerToken, Long workspaceId, String email, WorkspaceRole role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteRequest(email, role))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private void accept(String memberToken, Long workspaceId, Long memberId) throws Exception {
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, memberId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceMemberUpdateRequest(
                                null,
                                WorkspaceMemberStatus.ACTIVE
                        ))))
                .andExpect(status().isOk());
    }
}
