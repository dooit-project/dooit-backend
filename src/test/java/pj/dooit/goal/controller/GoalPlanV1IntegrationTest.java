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
import pj.dooit.Constant;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import pj.dooit.goal.service.GoalPlanOccurrenceService;
import pj.dooit.goal.dto.GoalPlanOccurrenceResponse;
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

    @Autowired
    GoalPlanOccurrenceService occurrenceService;

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


    @Test
    @DisplayName("선택 요일 회차를 중복 없이 생성하고 알림 후보를 제공한다")
    void occurrences_selectedDaysAndIdempotency() throws Exception {
        String token = accessToken("occ-days@example.com");
        Long goalId = createGoal(token);
        Long planId = createPlanForGoal(token, goalId, "주말 계획", List.of(ScheduleDay.SA, ScheduleDay.SU));
        String first = mockMvc.perform(get("/api/v1/goal-plan-occurrences")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-10-14").param("to", "2026-10-20").param("goalId", goalId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].serviceDate").value("2026-10-17"))
                .andExpect(jsonPath("$.data[1].serviceDate").value("2026-10-18"))
                .andExpect(jsonPath("$.data[0].notificationKey").value("goal-plan:" + planId + ":2026-10-17"))
                .andExpect(jsonPath("$.data[0].scheduledNotificationAt").value("2026-10-17T08:00:00"))
                .andExpect(jsonPath("$.data[0].snapshotGoalTitle").value("토익 900점"))
                .andReturn().getResponse().getContentAsString();
        Object firstIds = JsonPath.read(first, "$.data[*].id");
        String second = occurrences(token, "2026-10-14", "2026-10-20");
        org.junit.jupiter.api.Assertions.assertEquals(firstIds, JsonPath.read(second, "$.data[*].id"));
    }

    @Test
    @DisplayName("과거 스냅샷은 보존하고 이미 생성된 미래 회차에는 새 계획 버전을 적용한다")
    void occurrences_preserveHistoryAndRefreshFuture() throws Exception {
        String token = accessToken("occ-snapshot@example.com");
        Long goalId = createGoal(token);
        LocalDate today = LocalDate.now(Constant.ZONE);
        LocalDate past = today.minusDays(2);
        LocalDate future = today.plusDays(2);
        Long planId = createFlexiblePlan(token, goalId, past, "원래 계획", LocalTime.of(8, 0));
        String before = occurrences(token, past.toString(), future.toString());
        mockMvc.perform(patch("/api/v1/goal-plans/{id}", planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalPlanUpdateRequest(null, "독립 새 계획", null,
                                new GoalPlanScheduleRequest(past, List.of(ScheduleDay.values()), "Asia/Seoul", null),
                                new GoalPlanActionRequest("새 기본", null, null),
                                new GoalPlanActionRequest("새 최소", null, null), null, today))))
                .andExpect(status().isOk());
        String after = occurrences(token, past.toString(), future.toString());
        org.junit.jupiter.api.Assertions.assertEquals(
                (Object) JsonPath.read(before, "$.data[0].id"), (Object) JsonPath.read(after, "$.data[0].id"));
        org.junit.jupiter.api.Assertions.assertEquals("원래 계획", JsonPath.read(after, "$.data[0].snapshotPlanName"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (Integer) JsonPath.read(after, "$.data[0].planVersion"));
        org.junit.jupiter.api.Assertions.assertEquals("독립 새 계획", JsonPath.read(after, "$.data[4].snapshotPlanName"));
        org.junit.jupiter.api.Assertions.assertEquals(2, (Integer) JsonPath.read(after, "$.data[4].planVersion"));
        org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(after, "$.data[4].goalId"));
        org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(after, "$.data[4].scheduledNotificationAt"));
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", past.toString()).param("to", future.toString()).param("goalId", goalId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("정지·보관 계획은 미래 회차와 알림 후보를 제공하지 않고 과거 회차는 유지한다")
    void occurrences_pausedAndArchived() throws Exception {
        String token = accessToken("occ-pause@example.com");
        LocalDate today = LocalDate.now(Constant.ZONE);
        Long id = createFlexiblePlan(token, null, today.minusDays(1), "독립 계획", LocalTime.of(8, 0));
        occurrences(token, today.minusDays(1).toString(), today.plusDays(1).toString());
        for (GoalPlanStatus state : List.of(GoalPlanStatus.PAUSED, GoalPlanStatus.ARCHIVED)) {
            mockMvc.perform(patch("/api/v1/goal-plans/{id}/status", id)
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new GoalPlanStatusRequest(state))))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                            .param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].scheduledNotificationAt").isEmpty());
        }
    }

    @Test
    @DisplayName("조회 기간 검증과 사용자 소유권을 지킨다")
    void occurrences_validationAndOwnerScope() throws Exception {
        String owner = accessToken("occ-owner@example.com");
        String other = accessToken("occ-other@example.com");
        Long goalId = createGoal(owner);
        createFlexiblePlan(owner, goalId, LocalDate.of(2026, 10, 1), "계획", null);
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + other)
                        .param("from", "2026-10-14").param("to", "2026-10-20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + other)
                        .param("from", "2026-10-14").param("to", "2026-10-20").param("goalId", goalId.toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        for (String to : List.of("2026-10-13", "2026-11-14")) {
            mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + owner)
                            .param("from", "2026-10-14").param("to", to))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(10001));
        }
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").param("from", "2026-10-14").param("to", "2026-10-20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("목표 달성 시 futurePlanPolicy에 따라 미래 회차와 알림을 정지하거나 유지한다")
    void occurrences_futurePolicy() throws Exception {
        String token = accessToken("occ-policy@example.com");
        LocalDate today = LocalDate.now(Constant.ZONE);
        Long pauseGoal = createGoal(token);
        Long keepGoal = createGoal(token);
        createFlexiblePlan(token, pauseGoal, today, "정지 계획", LocalTime.of(8, 0));
        createFlexiblePlan(token, keepGoal, today, "유지 계획", LocalTime.of(8, 0));
        occurrences(token, today.toString(), today.plusDays(1).toString());
        for (Long goal : List.of(pauseGoal, keepGoal)) {
            mockMvc.perform(post("/api/v1/goals/{id}/achievement", goal)
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"futurePlanPolicy\":\"" + (goal.equals(pauseGoal) ? "PAUSE_FUTURE" : "KEEP_FUTURE") + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", today.toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].goalId").value(keepGoal))
                .andExpect(jsonPath("$.data[0].scheduledNotificationAt").value(notNullValue()));
    }

    @Test
    @DisplayName("동시 회차 조회도 계획과 날짜마다 하나의 회차만 생성한다")
    void occurrences_concurrentMaterialization() throws Exception {
        String token = accessToken("occ-concurrent@example.com");
        User owner = userRepository.findByEmail("occ-concurrent@example.com").orElseThrow();
        LocalDate date = LocalDate.now(Constant.ZONE);
        createFlexiblePlan(token, null, date, "동시 계획", null);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<List<GoalPlanOccurrenceResponse>> call = () -> {
                start.await();
                return occurrenceService.getOccurrences(date, date.plusDays(2), null, owner);
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertEquals(3, a.size());
            org.junit.jupiter.api.Assertions.assertEquals(a.stream().map(GoalPlanOccurrenceResponse::id).toList(),
                    b.stream().map(GoalPlanOccurrenceResponse::id).toList());
        }
    }


    @Test
    @DisplayName("미래 적용일 전에는 이전 버전을 사용하고 요일 변경으로 빠진 미래 회차는 숨긴다")
    void occurrences_futureEffectiveDateAndScheduleChanges() throws Exception {
        String token = accessToken("occ-effective@example.com");
        LocalDate start = LocalDate.now(Constant.ZONE).plusDays(3);
        Long planId = createFlexiblePlan(token, null, start, "원래", null);
        String before = occurrences(token, start.toString(), start.plusDays(2).toString());
        LocalDate effective = start.plusDays(1);
        mockMvc.perform(patch("/api/v1/goal-plans/{id}", planId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalPlanUpdateRequest(null, "새 계획", null,
                                new GoalPlanScheduleRequest(start, List.of(ScheduleDay.from(start.plusDays(2).getDayOfWeek())),
                                        "America/New_York", LocalTime.of(9, 30)),
                                new GoalPlanActionRequest("새 기본", null, null),
                                new GoalPlanActionRequest("새 최소", null, null), null, effective))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", start.toString()).param("to", start.plusDays(2).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].snapshotPlanName").value("원래"))
                .andExpect(jsonPath("$.data[1].snapshotPlanName").value("새 계획"))
                .andExpect(jsonPath("$.data[1].id").value((Number) JsonPath.read(before, "$.data[2].id")))
                .andExpect(jsonPath("$.data[1].timeZone").value("America/New_York"))
                .andExpect(jsonPath("$.data[1].scheduledNotificationAt").value(start.plusDays(2) + "T09:30:00"));
    }

    @Test
    @DisplayName("목표 제목 변경은 적용일 이후의 회차에만 반영하고 과거 목표 스냅샷을 유지한다")
    void occurrences_goalSnapshots() throws Exception {
        String token = accessToken("occ-goal-snapshot@example.com");
        Long goalId = createGoal(token);
        LocalDate today = LocalDate.now(Constant.ZONE);
        createFlexiblePlan(token, goalId, today.minusDays(1), "계획", null);
        occurrences(token, today.minusDays(1).toString(), today.plusDays(1).toString());
        var update = new pj.dooit.goal.dto.GoalUpdateRequest("새 목표", null, null, null, null, today.plusDays(1));
        mockMvc.perform(patch("/api/v1/goals/{id}", goalId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", today.minusDays(1).toString()).param("to", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].snapshotGoalTitle").value("토익 900점"))
                .andExpect(jsonPath("$.data[1].snapshotGoalTitle").value("토익 900점"))
                .andExpect(jsonPath("$.data[2].snapshotGoalTitle").value("새 목표"))
                .andExpect(jsonPath("$.data[2].goalVersion").value(2));
    }

    private String occurrences(String token, String from, String to) throws Exception {
        return mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", from).param("to", to))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Long createFlexiblePlan(String token, Long goalId, LocalDate start, String name, LocalTime notify) throws Exception {
        var request = new GoalPlanCreateRequest(goalId, name, null,
                new GoalPlanScheduleRequest(start, List.of(ScheduleDay.values()), "Asia/Seoul", notify),
                new GoalPlanActionRequest("기본", null, null), new GoalPlanActionRequest("최소", null, null), null);
        String response = mockMvc.perform(post("/api/v1/goal-plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
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
