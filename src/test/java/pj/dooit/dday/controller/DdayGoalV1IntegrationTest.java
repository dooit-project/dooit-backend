package pj.dooit.dday.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.dday.dto.DdayGoalRequest;
import pj.dooit.dday.dto.DdayGoalTaskRequest;
import pj.dooit.mail.MailService;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DdayGoalV1IntegrationTest {

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
    @DisplayName("v1 D-Day 목표 생성 응답은 nullable 필드 없이 계약 필드를 반환한다")
    void create_responseFields_nonNull() throws Exception {
        User owner = userRepository.save(new User("dday-v1@example.com", "encoded-password", "D-Day 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        DdayGoalRequest request = new DdayGoalRequest("출시", LocalDate.of(2026, 8, 1));

        mockMvc.perform(post("/api/v1/dday-goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.title").value("출시"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.daysLeft").value(notNullValue()))
                .andExpect(jsonPath("$.data.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("v1 D-Day 목표 삭제 응답은 data null envelope를 반환한다")
    void delete_success_dataNull() throws Exception {
        User owner = userRepository.save(new User("dday-delete@example.com", "encoded-password", "D-Day 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        Long goalId = createGoal(accessToken, new DdayGoalRequest("삭제 목표", LocalDate.of(2026, 8, 1)));

        mockMvc.perform(delete("/api/v1/dday-goals/{id}", goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    @Test
    @DisplayName("v1 D-Day 연결 Task를 Today로 이동해도 D-Day 응답 필드를 유지한다")
    void moveDdayConnectedTaskToToday_success() throws Exception {
        User owner = userRepository.save(new User("dday-task-today@example.com", "encoded-password", "D-Day 사용자"));
        String accessToken = jwtTokenService.createAccessToken(owner).tokenValue();
        Long goalId = createGoal(accessToken, new DdayGoalRequest("출시", LocalDate.of(2026, 8, 1)));
        Long taskId = createTodayTask(accessToken, goalId, new DdayGoalTaskRequest("출시 준비", LocalDate.of(2026, 7, 22)));

        mockMvc.perform(patch("/api/v1/tasks/{id}/today", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-23"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-07-23"))
                .andExpect(jsonPath("$.data.ddayGoalId").value(goalId))
                .andExpect(jsonPath("$.data.ddayGoalTitle").value("출시"))
                .andExpect(jsonPath("$.data.ddayGoalTargetDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.ddayDaysLeft").value(notNullValue()));
    }

    private Long createGoal(String accessToken, DdayGoalRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/dday-goals")
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

    private Long createTodayTask(String accessToken, Long goalId, DdayGoalTaskRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/dday-goals/{id}/tasks", goalId)
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
}
