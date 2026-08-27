package com.todolab.calendar.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.mail.MailService;
import com.todolab.task.domain.TaskType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarFeedV1IntegrationTest {

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
    @DisplayName("v1 calendar feed token을 발급하고 인증 없이 ICS를 조회한다")
    void calendarFeed_issueAndReadIcs() throws Exception {
        String accessToken = accessToken("calendar-feed@example.com");
        createTask(accessToken, new TaskRequest(
                "외부 캘린더 일정",
                "설명은 feed에 노출하지 않는다",
                TaskType.SCHEDULE,
                LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0),
                null,
                false
        ));

        String response = mockMvc.perform(post("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.feedPath").value(containsString("/api/v1/calendar-feeds/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.data.token");

        String ics = mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"todolab.ics\""))
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("SUMMARY:외부 캘린더 일정")
                .contains("UID:task-")
                .doesNotContain("설명은 feed에 노출하지 않는다");
    }

    @Test
    @DisplayName("calendar feed token 재발급은 기존 token을 폐기한다")
    void calendarFeed_reissueRevokesPreviousToken() throws Exception {
        String accessToken = accessToken("calendar-feed-reissue@example.com");
        String firstToken = issueToken(accessToken);
        String secondToken = issueToken(accessToken);

        assertThat(firstToken).isNotEqualTo(secondToken);

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", firstToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", secondToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("calendar feed token 폐기 후 feed 조회는 404를 반환한다")
    void calendarFeed_revoke() throws Exception {
        String accessToken = accessToken("calendar-feed-revoke@example.com");
        String token = issueToken(accessToken);

        mockMvc.perform(delete("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("workspace calendar feed token은 workspace 일정만 ICS에 포함한다")
    void workspaceCalendarFeed_issueAndReadIcs() throws Exception {
        String accessToken = accessToken("workspace-calendar-feed@example.com");
        Long workspaceId = createWorkspace(accessToken, "가족 일정");
        createTask(accessToken, new TaskRequest(
                "개인 일정",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.now().plusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0),
                null,
                null,
                false
        ));
        createWorkspaceTask(accessToken, workspaceId, new TaskRequest(
                "공유 외부 캘린더 일정",
                "workspace 설명은 feed에 노출하지 않는다",
                TaskType.SCHEDULE,
                LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0),
                null,
                false
        ));

        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/calendar-feed/token", workspaceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.feedPath").value(containsString("/api/v1/calendar-feeds/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.data.token");

        String ics = mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(ics)
                .contains("X-WR-CALNAME:ToDoLab - 가족 일정")
                .contains("SUMMARY:공유 외부 캘린더 일정")
                .doesNotContain("SUMMARY:개인 일정")
                .doesNotContain("workspace 설명은 feed에 노출하지 않는다");
    }

    @Test
    @DisplayName("workspace calendar feed token 폐기 후 feed 조회는 404를 반환한다")
    void workspaceCalendarFeed_revoke() throws Exception {
        String accessToken = accessToken("workspace-calendar-feed-revoke@example.com");
        Long workspaceId = createWorkspace(accessToken, "팀 일정");
        String token = issueWorkspaceToken(accessToken, workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/calendar-feed/token", workspaceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("workspace 멤버 제거는 해당 멤버의 workspace feed token을 폐기한다")
    void workspaceCalendarFeed_memberRemovalRevokesToken() throws Exception {
        String ownerToken = accessToken("workspace-calendar-feed-owner@example.com");
        String memberEmail = "workspace-calendar-feed-member@example.com";
        String memberToken = accessToken(memberEmail);
        Long workspaceId = createWorkspace(ownerToken, "팀 일정");
        Long memberId = invite(ownerToken, workspaceId, memberEmail, WorkspaceRole.VIEWER);
        accept(memberToken, workspaceId, memberId);
        String token = issueWorkspaceToken(memberToken, workspaceId);

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, memberId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isNotFound());
    }

    private String issueToken(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private String issueWorkspaceToken(String accessToken, Long workspaceId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/calendar-feed/token", workspaceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private void createTask(String accessToken, TaskRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private void createWorkspaceTask(String accessToken, Long workspaceId, TaskRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/tasks", workspaceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Calendar 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }
}
