package pj.dooit.goal.controller;

import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.dday.domain.DdayGoal;
import pj.dooit.dday.repository.DdayGoalRepository;
import pj.dooit.goal.domain.GoalFuturePlanPolicy;
import pj.dooit.goal.domain.GoalStatus;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalFuturePlanPolicyRequest;
import pj.dooit.goal.dto.GoalStatusRequest;
import pj.dooit.goal.dto.GoalUpdateRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    DdayGoalRepository ddayGoalRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("목표는 목표일 없이 생성하고 수정하면 version이 증가한다")
    void createAndUpdate_withoutTargetDate_success() throws Exception {
        User owner = user("goal-create@example.com");
        String accessToken = accessToken(owner);

        Long goalId = createGoal(accessToken, null, null);

        mockMvc.perform(get("/api/v1/goals")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(goalId))
                .andExpect(jsonPath("$.data[0].title").value("토익 900점"))
                .andExpect(jsonPath("$.data[0].targetDate").isEmpty())
                .andExpect(jsonPath("$.data[0].currentVersion").value(1));

        GoalUpdateRequest updateRequest = new GoalUpdateRequest(
                "토익 900점 달성",
                "LC/RC 합산 900점 이상",
                "지원 자격을 맞추기 위해",
                LocalDate.of(2026, 11, 30),
                null,
                LocalDate.of(2026, 10, 15)
        );

        mockMvc.perform(patch("/api/v1/goals/{goalId}", goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("토익 900점 달성"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-11-30"))
                .andExpect(jsonPath("$.data.currentVersion").value(2));
    }

    @Test
    @DisplayName("목표 달성 확인과 취소는 사용자 명시 action으로만 바뀐다")
    void achievementAndCancel_success() throws Exception {
        User owner = user("goal-achievement@example.com");
        String accessToken = accessToken(owner);
        Long goalId = createGoal(accessToken, LocalDate.of(2026, 11, 30), null);

        GoalFuturePlanPolicyRequest request = new GoalFuturePlanPolicyRequest(GoalFuturePlanPolicy.PAUSE_FUTURE);

        mockMvc.perform(post("/api/v1/goals/{goalId}/achievement", goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACHIEVED"))
                .andExpect(jsonPath("$.data.achievedAt").value(notNullValue()));

        mockMvc.perform(delete("/api/v1/goals/{goalId}/achievement", goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.achievedAt").isEmpty());
    }

    @Test
    @DisplayName("목표 상태 변경은 futurePlanPolicy 없이도 기본 정책으로 처리한다")
    void changeStatus_defaultFuturePolicy_success() throws Exception {
        User owner = user("goal-status@example.com");
        String accessToken = accessToken(owner);
        Long goalId = createGoal(accessToken, LocalDate.of(2026, 11, 30), null);

        GoalStatusRequest request = new GoalStatusRequest(GoalStatus.PAUSED, null);

        mockMvc.perform(patch("/api/v1/goals/{goalId}/status", goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    @Test
    @DisplayName("다른 사용자의 목표는 조회할 수 없다")
    void ownerIsolation_notFound() throws Exception {
        User owner = user("goal-owner@example.com");
        User other = user("goal-other@example.com");
        Long goalId = createGoal(accessToken(owner), LocalDate.of(2026, 11, 30), null);

        mockMvc.perform(get("/api/v1/goals/{goalId}", goalId)
                        .header("Authorization", "Bearer " + accessToken(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(70001));
    }

    @Test
    @DisplayName("D-Day 연결은 본인 소유 목표만 허용한다")
    void linkedDdayGoal_ownerValidation() throws Exception {
        User owner = user("goal-dday-owner@example.com");
        User other = user("goal-dday-other@example.com");
        DdayGoal otherDday = ddayGoalRepository.save(new DdayGoal(
                "남의 시험",
                LocalDate.of(2026, 11, 30),
                other
        ));

        GoalCreateRequest request = new GoalCreateRequest(
                "토익 900점",
                null,
                null,
                null,
                otherDday.getId()
        );

        mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + accessToken(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    private Long createGoal(String accessToken, LocalDate targetDate, Long linkedDdayGoalId) throws Exception {
        GoalCreateRequest request = new GoalCreateRequest(
                "토익 900점",
                "LC/RC 합산 900점 이상",
                "지원 자격을 맞추기 위해",
                targetDate,
                linkedDdayGoalId
        );

        String response = mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = com.jayway.jsonpath.JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private User user(String email) {
        return userRepository.save(new User(email, "encoded-password", "Goal 사용자"));
    }

    private String accessToken(User user) {
        return jwtTokenService.createAccessToken(user).tokenValue();
    }
}
