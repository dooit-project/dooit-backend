package pj.dooit.task.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskOwnershipV1IntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService jwtTokenService;
    @MockitoBean MailService mailService;

    private String ownerToken;
    private String otherToken;
    private long taskId;
    private long openItemId;
    private long doneItemId;
    private Object taskBefore;
    private Object checklistBefore;

    @BeforeEach
    void setUp() throws Exception {
        ownerToken = token("A");
        otherToken = token("B");
        taskId = createTask(ownerToken);
        openItemId = createItem("A의 비공개 항목");
        doneItemId = createItem("A의 완료 항목");
        mockMvc.perform(patch(itemPath(doneItemId) + "/done")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("completedAt", "2026-10-25T09:00:00"))
                .andExpect(status().isOk());
        taskBefore = data(get(taskPath()), ownerToken);
        checklistBefore = data(get(checklistPath()), ownerToken);
    }

    enum Operation {
        TASK_READ, TASK_UPDATE, TASK_DELETE, TASK_COMPLETE,
        CHECKLIST_READ, CHECKLIST_CREATE, CHECKLIST_UPDATE, CHECKLIST_DELETE,
        CHECKLIST_COMPLETE, CHECKLIST_REOPEN, CHECKLIST_REORDER
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    @DisplayName("B가 A의 Task ID로 요청하면 404이고 Task와 체크리스트 원본은 유지된다")
    void otherAccountCannotReadOrMutate(Operation operation) throws Exception {
        String response = mockMvc.perform(operationRequest(operation)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20001))
                .andExpect(jsonPath("$.data").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("A의 비공개", "A의 완료 항목", "비공개 설명");
        assertOwnerDataUnchanged();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    @DisplayName("인증 없는 개인 Task와 체크리스트 요청은 401이고 원본은 유지된다")
    void anonymousCannotReadOrMutate(Operation operation) throws Exception {
        mockMvc.perform(operationRequest(operation))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.data").isEmpty());
        assertOwnerDataUnchanged();
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {
            "CHECKLIST_UPDATE", "CHECKLIST_DELETE", "CHECKLIST_COMPLETE", "CHECKLIST_REOPEN"
    })
    @DisplayName("B 소유 Task 경로에 A의 item ID를 끼워 넣어도 조회·변경할 수 없다")
    void checklistItemMustBelongToAuthorizedParent(Operation operation) throws Exception {
        long otherTaskId = createTask(otherToken);
        Object otherTaskBefore = data(get("/api/v1/tasks/" + otherTaskId), otherToken);
        String suffix = switch (operation) {
            case CHECKLIST_COMPLETE -> "/done";
            case CHECKLIST_REOPEN -> "/done/cancel";
            default -> "";
        };
        HttpMethod method = switch (operation) {
            case CHECKLIST_UPDATE -> HttpMethod.PUT;
            case CHECKLIST_DELETE -> HttpMethod.DELETE;
            default -> HttpMethod.PATCH;
        };
        long itemId = operation == Operation.CHECKLIST_REOPEN ? doneItemId : openItemId;
        mockMvc.perform(request(method, "/api/v1/tasks/" + otherTaskId + "/checklist-items/" + itemId + suffix)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"변조 시도\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20004))
                .andExpect(jsonPath("$.data").isEmpty());
        assertOwnerDataUnchanged();
        assertThat(data(get("/api/v1/tasks/" + otherTaskId), otherToken)).isEqualTo(otherTaskBefore);
        assertThat((java.util.List<?>) data(get("/api/v1/tasks/" + otherTaskId + "/checklist-items"), otherToken)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"GET,true", "PUT,true", "DELETE,true", "PATCH,true",
            "GET,false", "PUT,false", "DELETE,false", "PATCH,false"})
    @DisplayName("소유권 없는 legacy Task 경로로 v1 보호를 우회할 수 없다")
    void legacyTaskRoutesCannotBypassOwnership(String method, boolean authenticated) throws Exception {
        String path = "/api/tasks/" + taskId + (method.equals("PATCH") ? "/done" : "");
        var request = request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"변조 시도\"}");
        if (authenticated) request.header("Authorization", "Bearer " + otherToken);
        mockMvc.perform(request)
                .andExpect(status().is(authenticated ? 403 : 401))
                .andExpect(jsonPath("$.data").isEmpty());
        assertOwnerDataUnchanged();
    }

    private MockHttpServletRequestBuilder operationRequest(Operation operation) {
        return switch (operation) {
            case TASK_READ -> get(taskPath());
            case TASK_UPDATE -> put(taskPath()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"변조 시도\",\"description\":\"변경 설명\",\"type\":\"IDEA\",\"allDay\":false}");
            case TASK_DELETE -> delete(taskPath());
            case TASK_COMPLETE -> patch(taskPath() + "/done").param("completedAt", "2026-10-25T10:00:00");
            case CHECKLIST_READ -> get(checklistPath());
            case CHECKLIST_CREATE -> post(checklistPath()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"침입 항목\"}");
            case CHECKLIST_UPDATE -> put(itemPath(openItemId)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"변조 시도\"}");
            case CHECKLIST_DELETE -> delete(itemPath(openItemId));
            case CHECKLIST_COMPLETE -> patch(itemPath(openItemId) + "/done");
            case CHECKLIST_REOPEN -> patch(itemPath(doneItemId) + "/done/cancel");
            case CHECKLIST_REORDER -> put(checklistPath() + "/order").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"orderedItemIds\":[" + doneItemId + "," + openItemId + "]}");
        };
    }

    private void assertOwnerDataUnchanged() throws Exception {
        // 각 HTTP 요청은 별도 트랜잭션이다. 거부 후 A로 재조회해 영속화된 상태 전체를 비교한다.
        assertThat(data(get(taskPath()), ownerToken)).isEqualTo(taskBefore);
        assertThat(data(get(checklistPath()), ownerToken)).isEqualTo(checklistBefore);
    }

    private Object data(MockHttpServletRequestBuilder request, String token) throws Exception {
        String response = mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data");
    }

    private String token(String name) {
        User user = userRepository.save(new User(UUID.randomUUID() + "@example.com", "encoded-password", name));
        return jwtTokenService.createAccessToken(user).tokenValue();
    }

    private long createTask(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"A의 비공개 Task\",\"description\":\"비공개 설명\",\"type\":\"TODO\",\"allDay\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private long createItem(String title) throws Exception {
        String response = mockMvc.perform(post(checklistPath())
                        .header("Authorization", "Bearer " + ownerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private String taskPath() { return "/api/v1/tasks/" + taskId; }
    private String checklistPath() { return taskPath() + "/checklist-items"; }
    private String itemPath(long id) { return checklistPath() + "/" + id; }
}
