package com.todolab.workspace.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.dday.repository.DdayGoalRepository;
import com.todolab.mail.MailService;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import com.todolab.workspace.domain.WorkspaceMemberStatus;
import com.todolab.workspace.domain.WorkspaceRole;
import com.todolab.workspace.dto.WorkspaceInviteRequest;
import com.todolab.workspace.dto.WorkspaceMemberUpdateRequest;
import com.todolab.workspace.dto.WorkspaceRequest;
import com.todolab.workspace.repository.SharedWorkspaceRepository;
import com.todolab.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
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
class WorkspaceV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    WorkspaceMemberRepository workspaceMemberRepository;

    @Autowired
    SharedWorkspaceRepository sharedWorkspaceRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        workspaceMemberRepository.deleteAll();
        taskRepository.deleteAll();
        ddayGoalRepository.deleteAll();
        recurrenceSeriesRepository.deleteAll();
        sharedWorkspaceRepository.deleteAll();
    }

    @Test
    @DisplayName("v1 Workspace 생성은 생성자를 OWNER ACTIVE 멤버로 등록한다")
    void createWorkspace_success_ownerMember() throws Exception {
        String ownerToken = accessToken("workspace-owner-create@example.com");

        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("가족 일정", "공유 일정"));

        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(workspaceId))
                .andExpect(jsonPath("$.data[0].name").value("가족 일정"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("v1 Workspace 초대 수락 후 멤버가 workspace를 조회한다")
    void inviteAndAcceptMember_success() throws Exception {
        String ownerToken = accessToken("workspace-owner-invite@example.com");
        String memberToken = accessToken("workspace-member-invite@example.com");
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("팀 일정", null));

        String inviteResponse = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteRequest(
                                "workspace-member-invite@example.com",
                                WorkspaceRole.EDITOR
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.role").value("EDITOR"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number memberId = JsonPath.read(inviteResponse, "$.data.id");

        mockMvc.perform(get("/api/v1/workspace-invitations")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].workspace.id").value(workspaceId))
                .andExpect(jsonPath("$.data[0].membership.id").value(memberId.longValue()))
                .andExpect(jsonPath("$.data[0].membership.status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].invitedAt").value(notNullValue()));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(50001));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, memberId.longValue())
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceMemberUpdateRequest(
                                null,
                                WorkspaceMemberStatus.ACTIVE
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspaceId));

        mockMvc.perform(get("/api/v1/workspace-invitations")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("v1 Workspace 초대 목록은 REMOVED membership을 반환하지 않는다")
    void findInvitations_excludesRemovedMembership() throws Exception {
        String ownerToken = accessToken("workspace-owner-removed-invite@example.com");
        String memberToken = accessToken("workspace-member-removed-invite@example.com");
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("초대 제거", null));
        Long memberId = invite(ownerToken, workspaceId, "workspace-member-removed-invite@example.com", WorkspaceRole.VIEWER);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, memberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/workspace-invitations")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("v1 Workspace EDITOR는 멤버를 초대할 수 없다")
    void inviteMember_forbiddenForEditor() throws Exception {
        String ownerToken = accessToken("workspace-owner-editor@example.com");
        String editorToken = accessToken("workspace-editor@example.com");
        userRepository.save(new User("workspace-new-member@example.com", "encoded-password", "새 멤버"));
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("팀 일정", null));
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-editor@example.com", WorkspaceRole.EDITOR);
        accept(editorToken, workspaceId, editorMemberId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteRequest(
                                "workspace-new-member@example.com",
                                WorkspaceRole.VIEWER
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace EDITOR는 workspace 수정과 삭제를 할 수 없다")
    void mutateWorkspace_forbiddenForEditor() throws Exception {
        String ownerToken = accessToken("workspace-owner-mutate-forbidden@example.com");
        String editorToken = accessToken("workspace-editor-mutate-forbidden@example.com");
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("팀 일정", null));
        Long editorMemberId = invite(ownerToken, workspaceId, "workspace-editor-mutate-forbidden@example.com", WorkspaceRole.EDITOR);
        accept(editorToken, workspaceId, editorMemberId);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceRequest("변경 시도", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace 마지막 ACTIVE OWNER는 강등, 제거, 탈퇴할 수 없다")
    void lastActiveOwnerMutation_invalidInput() throws Exception {
        String ownerToken = accessToken("workspace-owner-last@example.com");
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("마지막 owner", null));
        Long ownerMemberId = firstMemberId(ownerToken, workspaceId);

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, ownerMemberId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceMemberUpdateRequest(
                                WorkspaceRole.VIEWER,
                                null
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));

        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, ownerMemberId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceMemberUpdateRequest(
                                null,
                                WorkspaceMemberStatus.REMOVED
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, ownerMemberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    @Test
    @DisplayName("v1 Workspace는 게스트 계정 사용을 거부한다")
    void createWorkspace_forbiddenForGuest() throws Exception {
        User guest = userRepository.save(User.guest(LocalDateTime.of(2026, 9, 13, 0, 0)));
        String guestToken = jwtTokenService.createGuestAccessToken(guest).tokenValue();

        mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceRequest("게스트 공유", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));

        mockMvc.perform(get("/api/v1/workspace-invitations")
                        .header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));
    }

    @Test
    @DisplayName("v1 Workspace OWNER는 workspace 정보를 수정하고 삭제한다")
    void updateAndDeleteWorkspace_success() throws Exception {
        String ownerToken = accessToken("workspace-owner-update@example.com");
        Long workspaceId = createWorkspace(ownerToken, new WorkspaceRequest("기존 이름", null));

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceRequest("새 이름", "설명"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새 이름"))
                .andExpect(jsonPath("$.data.description").value("설명"));

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        org.assertj.core.api.Assertions.assertThat(sharedWorkspaceRepository.findById(workspaceId)).isEmpty();
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Workspace 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createWorkspace(String accessToken, WorkspaceRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces")
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

    private Long firstMemberId(String accessToken, Long workspaceId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data[0].id");
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
