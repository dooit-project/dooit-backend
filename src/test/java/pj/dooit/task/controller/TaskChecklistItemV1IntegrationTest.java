package pj.dooit.task.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.task.dto.TaskChecklistItemOrderRequest;
import pj.dooit.task.dto.TaskChecklistItemRequest;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
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
}
