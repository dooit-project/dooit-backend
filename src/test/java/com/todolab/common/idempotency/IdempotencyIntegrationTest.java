package com.todolab.common.idempotency;

import com.todolab.auth.service.JwtTokenService;
import com.todolab.config.CorsConfig;
import com.todolab.mail.MailService;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.repository.TaskRepository;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdempotencyIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 같은 payload는 최초 Task 생성 응답을 replay한다")
    void sameKeyAndPayload_replaysFirstResponse() throws Exception {
        AuthFixture auth = accessToken("idempotent-task@example.com");
        String key = UUID.randomUUID().toString();
        TaskRequest request = new TaskRequest("중복 방지", null, null, null, null, false);
        String payload = objectMapper.writeValueAsString(request);

        String firstBody = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long firstTaskId = readTaskId(firstBody);

        String secondBody = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.data.id").value(firstTaskId))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(readTaskId(secondBody)).isEqualTo(firstTaskId);
        assertThat(taskRepository.findByOwnerId(auth.userId())).hasSize(1);
    }

    @Test
    @DisplayName("같은 Idempotency-Key와 다른 payload는 409를 반환한다")
    void sameKeyAndDifferentPayload_returnsConflict() throws Exception {
        AuthFixture auth = accessToken("idempotent-task-conflict@example.com");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "첫 요청",
                                null,
                                null,
                                null,
                                null,
                                false
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest(
                                "다른 요청",
                                null,
                                null,
                                null,
                                null,
                                false
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(10004));

        assertThat(taskRepository.findByOwnerId(auth.userId())).hasSize(1);
    }

    private AuthFixture accessToken(String email) {
        User user = userRepository.save(new User(email, "encoded-password", "테스터"));
        return new AuthFixture(user.getId(), jwtTokenService.createAccessToken(user).tokenValue());
    }

    private Long readTaskId(String body) {
        JsonNode node = objectMapper.readTree(body);
        return node.get("data").get("id").longValue();
    }

    private record AuthFixture(Long userId, String accessToken) {
    }
}
