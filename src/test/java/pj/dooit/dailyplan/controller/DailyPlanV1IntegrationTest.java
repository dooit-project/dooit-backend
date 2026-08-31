package pj.dooit.dailyplan.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.dailyplan.domain.DailyPlanStatus;
import pj.dooit.dailyplan.dto.DailyPlanRequest;
import pj.dooit.mail.MailService;
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

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DailyPlanV1IntegrationTest {

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
    @DisplayName("v1 Daily Plan 조회는 저장 전 빈 DRAFT 계획을 반환한다")
    void getDailyPlan_emptyDraft() throws Exception {
        String accessToken = accessToken("daily-plan-empty@example.com");

        mockMvc.perform(get("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-08-31"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.focusTaskIds.length()").value(0))
                .andExpect(jsonPath("$.data.confirmedAt").isEmpty())
                .andExpect(jsonPath("$.data.closedAt").isEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isEmpty());
    }

    @Test
    @DisplayName("v1 Daily Plan 저장은 focus Task 순서와 상태 시각을 보존한다")
    void replaceDailyPlan_confirmed() throws Exception {
        String accessToken = accessToken("daily-plan-confirmed@example.com");
        Long firstId = createTodayTask(accessToken, "첫 번째", "2026-08-31");
        Long secondId = createTodayTask(accessToken, "두 번째", "2026-08-31");
        DailyPlanRequest request = new DailyPlanRequest(List.of(secondId, firstId), DailyPlanStatus.CONFIRMED);

        mockMvc.perform(put("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-08-31"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.focusTaskIds[0]").value(secondId))
                .andExpect(jsonPath("$.data.focusTaskIds[1]").value(firstId))
                .andExpect(jsonPath("$.data.confirmedAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.closedAt").isEmpty());

        mockMvc.perform(get("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.focusTaskIds[0]").value(secondId))
                .andExpect(jsonPath("$.data.focusTaskIds[1]").value(firstId));
    }

    @Test
    @DisplayName("v1 Daily Plan focusTaskIds는 같은 날짜 미완료 Today Task만 허용한다")
    void replaceDailyPlan_invalidFocusTask_badRequest() throws Exception {
        String accessToken = accessToken("daily-plan-validation@example.com");
        Long todayTaskId = createTodayTask(accessToken, "오늘", "2026-08-31");
        Long otherDateTaskId = createTodayTask(accessToken, "다른 날", "2026-09-01");
        DailyPlanRequest request = new DailyPlanRequest(List.of(todayTaskId, otherDateTaskId), DailyPlanStatus.DRAFT);

        mockMvc.perform(put("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    @Test
    @DisplayName("v1 Daily Plan focus Task는 완료, Inbox 이동, 날짜 이동 시 자동 제거된다")
    void focusTask_autoRemovedWhenTaskLeavesToday() throws Exception {
        String accessToken = accessToken("daily-plan-cleanup@example.com");
        Long doneTaskId = createTodayTask(accessToken, "완료", "2026-08-31");
        Long inboxTaskId = createTodayTask(accessToken, "인박스", "2026-08-31");
        Long movedTaskId = createTodayTask(accessToken, "이동", "2026-08-31");
        DailyPlanRequest request = new DailyPlanRequest(List.of(doneTaskId, inboxTaskId, movedTaskId), DailyPlanStatus.CONFIRMED);

        mockMvc.perform(put("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.focusTaskIds.length()").value(3));

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", doneTaskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-08-31T18:00:00"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/inbox", inboxTaskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/today", movedTaskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-09-01"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/daily-plans/{date}", "2026-08-31")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.focusTaskIds.length()").value(0));
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Daily Plan 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createTodayTask(String accessToken, String title, String date) throws Exception {
        Long taskId = createInboxTask(accessToken, title);
        String response = mockMvc.perform(patch("/api/v1/tasks/{id}/today", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", date))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long createInboxTask(String accessToken, String title) throws Exception {
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
}
