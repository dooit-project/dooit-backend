package com.todolab.workspace.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.dday.dto.DdayGoalRequest;
import com.todolab.mail.MailService;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceDdayGoalV1IntegrationTest {

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
    @DisplayName("v1 Workspace D-Day는 OWNER가 생성하고 VIEWER가 조회한다")
    void createAndReadWorkspaceDday_success() throws Exception {
        String ownerToken = accessToken("workspace-dday-owner@example.com");
        String viewerToken = accessToken("workspace-dday-viewer@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 목표");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-dday-viewer@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);

        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dday-goals", workspaceId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(goalId))
                .andExpect(jsonPath("$.data[0].title").value("공유 출시"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}", workspaceId, goalId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(goalId))
                .andExpect(jsonPath("$.data.targetDate").value("2026-09-30"));

        mockMvc.perform(get("/api/v1/dday-goals")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("v1 Workspace D-Day 생성은 VIEWER에게 403을 반환한다")
    void createWorkspaceDday_forbiddenForViewer() throws Exception {
        String ownerToken = accessToken("workspace-dday-owner-forbidden@example.com");
        String viewerToken = accessToken("workspace-dday-viewer-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 목표");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-dday-viewer-forbidden@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/dday-goals", workspaceId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DdayGoalRequest(
                                "공유 출시",
                                LocalDate.of(2026, 9, 30)
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace D-Day는 EDITOR가 삭제한다")
    void deleteWorkspaceDday_successForEditor() throws Exception {
        String ownerToken = accessToken("workspace-dday-owner-delete@example.com");
        String editorToken = accessToken("workspace-dday-editor-delete@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 목표");
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-dday-editor-delete@example.com", WorkspaceRole.EDITOR);
        accept(editorToken, workspaceId, editorMemberId);
        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}", workspaceId, goalId)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}", workspaceId, goalId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(30001));
    }

    @Test
    @DisplayName("v1 Workspace D-Day 삭제는 VIEWER에게 403을 반환한다")
    void deleteWorkspaceDday_forbiddenForViewer() throws Exception {
        String ownerToken = accessToken("workspace-dday-owner-delete-forbidden@example.com");
        String viewerToken = accessToken("workspace-dday-viewer-delete-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 목표");
        Long viewerMemberId = invite(ownerToken, workspaceId, "workspace-dday-viewer-delete-forbidden@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}", workspaceId, goalId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace D-Day 조회는 non-member에게 404를 반환한다")
    void readWorkspaceDday_notFoundForNonMember() throws Exception {
        String ownerToken = accessToken("workspace-dday-owner-nonmember@example.com");
        String outsiderToken = accessToken("workspace-dday-outsider@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 목표");
        Long goalId = createWorkspaceDday(ownerToken, workspaceId, new DdayGoalRequest(
                "공유 출시",
                LocalDate.of(2026, 9, 30)
        ));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/dday-goals/{goalId}", workspaceId, goalId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(50001));
    }

    private String accessToken(String email) {
        User user = userRepository.save(new User(email, "encoded-password", "Workspace D-Day 사용자"));
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
