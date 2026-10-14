package pj.dooit.goal.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.goal.domain.GoalPlanStatus;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalPlanStatusRequest;
import pj.dooit.goal.dto.GoalPlanUpdateRequest;
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
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalPlanV1IntegrationTest {

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
    @DisplayName("목표 아래 반복 계획을 여러 개 생성하고 목표별로 조회할 수 있다")
    void createMultiplePlansUnderGoal_success() throws Exception {
        String accessToken = accessToken("goal-plan-create@example.com");
        Long goalId = createGoal(accessToken);

        Long vocabularyPlanId = createPlanForGoal(accessToken, goalId, "매일 단어 30개", List.of(
                ScheduleDay.MO,
                ScheduleDay.TU,
                ScheduleDay.WE,
                ScheduleDay.TH,
                ScheduleDay.FR,
                ScheduleDay.SA,
                ScheduleDay.SU
        ));
        Long problemPlanId = createPlanForGoal(accessToken, goalId, "주말 문제 5개", List.of(ScheduleDay.SA, ScheduleDay.SU));

        mockMvc.perform(get("/api/v1/goals/{goalId}/plans", goalId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(problemPlanId))
                .andExpect(jsonPath("$.data[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data[0].name").value("주말 문제 5개"))
                .andExpect(jsonPath("$.data[1].id").value(vocabularyPlanId))
                .andExpect(jsonPath("$.data[1].name").value("매일 단어 30개"));
    }

    @Test
    @DisplayName("목표 없는 독립 반복 계획을 생성하고 independentOnly로 조회할 수 있다")
    void independentPlan_success() throws Exception {
        String accessToken = accessToken("goal-plan-independent@example.com");
        createGoal(accessToken);

        GoalPlanCreateRequest request = planRequest(null, "아침 스트레칭", List.of(ScheduleDay.MO, ScheduleDay.WE));

        mockMvc.perform(post("/api/v1/goal-plans")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.goalId").isEmpty())
                .andExpect(jsonPath("$.data.name").value("아침 스트레칭"));

        mockMvc.perform(get("/api/v1/goal-plans")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("independentOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].goalId").isEmpty())
                .andExpect(jsonPath("$.data[0].name").value("아침 스트레칭"));
    }

    @Test
    @DisplayName("계획 수정은 version을 올리고 미래 적용일을 받을 수 있다")
    void updatePlan_versions() throws Exception {
        String accessToken = accessToken("goal-plan-update@example.com");
        Long goalId = createGoal(accessToken);
        Long planId = createPlanForGoal(accessToken, goalId, "매일 단어 30개", List.of(ScheduleDay.MO));

        GoalPlanUpdateRequest request = new GoalPlanUpdateRequest(
                goalId,
                "매일 단어 50개",
                "출근 전",
                new GoalPlanScheduleRequest(
                        LocalDate.of(2026, 10, 20),
                        List.of(ScheduleDay.MO, ScheduleDay.TU),
                        "Asia/Seoul",
                        null
                ),
                new GoalPlanActionRequest("단어 50개 외우기", "50개", null),
                new GoalPlanActionRequest("단어 5개 보기", "5개", null),
                "Day 2 열기",
                LocalDate.of(2026, 10, 20)
        );

        mockMvc.perform(patch("/api/v1/goal-plans/{planId}", planId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(2))
                .andExpect(jsonPath("$.data.name").value("매일 단어 50개"))
                .andExpect(jsonPath("$.data.schedule.days[1]").value("TU"));
    }

    @Test
    @DisplayName("계획 상태를 독립적으로 일시정지하고 보관할 수 있다")
    void changePlanStatus_success() throws Exception {
        String accessToken = accessToken("goal-plan-status@example.com");
        Long goalId = createGoal(accessToken);
        Long planId = createPlanForGoal(accessToken, goalId, "매일 단어 30개", List.of(ScheduleDay.MO));

        mockMvc.perform(patch("/api/v1/goal-plans/{planId}/status", planId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalPlanStatusRequest(GoalPlanStatus.PAUSED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        mockMvc.perform(patch("/api/v1/goal-plans/{planId}/status", planId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalPlanStatusRequest(GoalPlanStatus.ARCHIVED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archivedAt").value(notNullValue()));
    }

    @Test
    @DisplayName("다른 사용자의 목표에는 계획을 만들 수 없다")
    void createPlan_otherGoal_notFound() throws Exception {
        String ownerToken = accessToken("goal-plan-owner@example.com");
        String otherToken = accessToken("goal-plan-other@example.com");
        Long ownerGoalId = createGoal(ownerToken);

        mockMvc.perform(post("/api/v1/goals/{goalId}/plans", ownerGoalId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(null, "남의 목표 계획", List.of(ScheduleDay.MO)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(70001));
    }

    private Long createGoal(String accessToken) throws Exception {
        GoalCreateRequest request = new GoalCreateRequest(
                "토익 900점",
                "LC/RC 합산 900점 이상",
                "지원 자격을 맞추기 위해",
                LocalDate.of(2026, 11, 30),
                null
        );

        String response = mockMvc.perform(post("/api/v1/goals")
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

    private Long createPlanForGoal(String accessToken, Long goalId, String name, List<ScheduleDay> days) throws Exception {
        String response = mockMvc.perform(post("/api/v1/goals/{goalId}/plans", goalId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(planRequest(null, name, days))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private GoalPlanCreateRequest planRequest(Long goalId, String name, List<ScheduleDay> days) {
        return new GoalPlanCreateRequest(
                goalId,
                name,
                "출근 전",
                new GoalPlanScheduleRequest(
                        LocalDate.of(2026, 10, 14),
                        days,
                        "Asia/Seoul",
                        LocalTime.of(8, 0)
                ),
                new GoalPlanActionRequest(name, "30개", null),
                new GoalPlanActionRequest("최소 5개 보기", "5개", null),
                "단어장 열기"
        );
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Goal Plan 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }
}
