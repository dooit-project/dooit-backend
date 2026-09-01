package pj.dooit.task.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.task.dto.TaskChecklistItemOrderRequest;
import pj.dooit.task.dto.TaskChecklistItemRequest;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import pj.dooit.workspace.domain.WorkspaceMemberStatus;
import pj.dooit.workspace.domain.WorkspaceRole;
import pj.dooit.workspace.dto.WorkspaceInviteRequest;
import pj.dooit.workspace.dto.WorkspaceMemberUpdateRequest;
import pj.dooit.workspace.dto.WorkspaceRequest;
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
class TaskChecklistItemV1IntegrationTest {

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
    @DisplayName("v1 Task checklist item CRUD와 재정렬은 로그인 사용자 Task 범위로 동작한다")
    void checklistCrudAndReorder_ownerScoped() throws Exception {
        String accessToken = accessToken("checklist-crud@example.com");
        Long taskId = createTask(accessToken, "체크리스트 대상");
        Long firstId = createItem(accessToken, taskId, "자료 확인");
        Long secondId = createItem(accessToken, taskId, "초안 작성");

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(secondId))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1));

        mockMvc.perform(put("/api/v1/tasks/{taskId}/checklist-items/{itemId}", taskId, firstId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemRequest("자료 재확인"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("자료 재확인"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/checklist-items/{itemId}/done", taskId, firstId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-09-01T09:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true))
                .andExpect(jsonPath("$.data.completedAt").value("2026-09-01T09:00:00"));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/checklist-items/{itemId}/done/cancel", taskId, firstId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.completedAt").isEmpty());

        mockMvc.perform(put("/api/v1/tasks/{taskId}/checklist-items/order", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemOrderRequest(List.of(secondId, firstId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(firstId))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1));

        mockMvc.perform(delete("/api/v1/tasks/{taskId}/checklist-items/{itemId}", taskId, secondId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0));
    }

    @Test
    @DisplayName("v1 Task checklist item은 다른 사용자의 Task에서 조회할 수 없다")
    void checklist_ownerIsolation() throws Exception {
        String ownerToken = accessToken("checklist-owner@example.com");
        String otherToken = accessToken("checklist-other@example.com");
        Long taskId = createTask(ownerToken, "소유자 Task");
        createItem(ownerToken, taskId, "비공개 item");

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001));
    }

    @Test
    @DisplayName("v1 Task 완료는 미완료 checklist item을 함께 완료한다")
    void completeTask_completesChecklistItems() throws Exception {
        String accessToken = accessToken("checklist-complete-task@example.com");
        Long taskId = createTask(accessToken, "완료 대상");
        Long firstId = createItem(accessToken, taskId, "자료 확인");
        Long secondId = createItem(accessToken, taskId, "초안 작성");

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-09-01T18:00:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].done").value(true))
                .andExpect(jsonPath("$.data[0].completedAt").value("2026-09-01T18:00:00"))
                .andExpect(jsonPath("$.data[1].id").value(secondId))
                .andExpect(jsonPath("$.data[1].done").value(true))
                .andExpect(jsonPath("$.data[1].completedAt").value("2026-09-01T18:00:00"));
    }

    @Test
    @DisplayName("v1 Workspace Task checklist item은 EDITOR가 변경하고 VIEWER가 조회한다")
    void workspaceChecklist_editorMutatesViewerReads() throws Exception {
        String ownerToken = accessToken("checklist-workspace-owner@example.com");
        String editorToken = accessToken("checklist-workspace-editor@example.com");
        String viewerToken = accessToken("checklist-workspace-viewer@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long editorMemberId = invite(ownerToken, workspaceId, "checklist-workspace-editor@example.com", WorkspaceRole.EDITOR);
        Long viewerMemberId = invite(ownerToken, workspaceId, "checklist-workspace-viewer@example.com", WorkspaceRole.VIEWER);
        accept(editorToken, workspaceId, editorMemberId);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, "공유 체크리스트 대상");

        Long firstId = createItem(editorToken, taskId, "자료 확인");
        Long secondId = createItem(editorToken, taskId, "초안 작성");

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[1].id").value(secondId));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/checklist-items/{itemId}/done", taskId, firstId)
                        .header("Authorization", "Bearer " + editorToken)
                        .param("completedAt", "2026-09-01T10:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true));

        mockMvc.perform(put("/api/v1/tasks/{taskId}/checklist-items/order", taskId)
                        .header("Authorization", "Bearer " + editorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemOrderRequest(List.of(secondId, firstId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[1].id").value(firstId));
    }

    @Test
    @DisplayName("v1 Workspace Task checklist item 변경은 VIEWER에게 403, non-member에게 404를 반환한다")
    void workspaceChecklist_permissionBoundaries() throws Exception {
        String ownerToken = accessToken("checklist-workspace-owner-boundary@example.com");
        String viewerToken = accessToken("checklist-workspace-viewer-boundary@example.com");
        String outsiderToken = accessToken("checklist-workspace-outsider-boundary@example.com");
        Long workspaceId = createWorkspace(ownerToken, "제품팀 일정");
        Long viewerMemberId = invite(ownerToken, workspaceId, "checklist-workspace-viewer-boundary@example.com", WorkspaceRole.VIEWER);
        accept(viewerToken, workspaceId, viewerMemberId);
        Long taskId = createWorkspaceTask(ownerToken, workspaceId, "공유 체크리스트 권한");
        Long itemId = createItem(ownerToken, taskId, "공유 item");

        mockMvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskChecklistItemRequest("viewer 생성"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));

        mockMvc.perform(patch("/api/v1/tasks/{taskId}/checklist-items/{itemId}/done", taskId, itemId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(11003));

        mockMvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001));
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Checklist 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createTask(String accessToken, String title) throws Exception {
        TaskRequest request = new TaskRequest(title, null, null, null, null, false);
        String response = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long createItem(String accessToken, Long taskId, String title) throws Exception {
        TaskChecklistItemRequest request = new TaskChecklistItemRequest(title);
        String response = mockMvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", taskId)
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

    private Long createWorkspaceTask(String accessToken, Long workspaceId, String title) throws Exception {
        TaskRequest request = new TaskRequest(title, null, null, null, null, false);
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

    private Long invite(String accessToken, Long workspaceId, String email, WorkspaceRole role) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/members", workspaceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceInviteRequest(email, role))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private void accept(String accessToken, Long workspaceId, Long memberId) throws Exception {
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/members/{memberId}", workspaceId, memberId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new WorkspaceMemberUpdateRequest(
                                null,
                                WorkspaceMemberStatus.ACTIVE
                        ))))
                .andExpect(status().isOk());
    }
}
