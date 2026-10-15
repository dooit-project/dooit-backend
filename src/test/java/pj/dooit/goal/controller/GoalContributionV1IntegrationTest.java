package pj.dooit.goal.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.Constant;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.config.CorsConfig;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalDirectLogRequest;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.repository.GoalDirectLogRepository;
import pj.dooit.goal.service.GoalDirectLogService;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalContributionV1IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired GoalDirectLogRepository logRepository;
    @Autowired GoalDirectLogService logService;
    @Autowired pj.dooit.goal.service.GoalTaskLinkService taskLinkService;
    @Autowired pj.dooit.task.service.TaskTxService taskTxService;
    @MockitoBean MailService mailService;

    @Test
    @DisplayName("목표에서 일회성 Today Task를 생성하고 생성 재시도를 중복 없이 처리한다")
    void createGoalTaskWithIdempotency() throws Exception {
        String token = token("contribution-create@example.com");
        Long goal = goal(token);
        String body = "{\"title\":\"문제 풀기\",\"date\":\"" + today() + "\"}";
        String first = mockMvc.perform(post("/api/v1/goals/{goalId}/tasks", goal)
                        .header("Authorization", "Bearer " + token).header(CorsConfig.IDEMPOTENCY_KEY_HEADER, "goal-task-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.linkedGoalId").value(goal))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.targetDate").value(today().toString()))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/goals/{goalId}/tasks", goal)
                        .header("Authorization", "Bearer " + token).header(CorsConfig.IDEMPOTENCY_KEY_HEADER, "goal-task-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.data.id").value(number(first, "$.data.id")));
        mockMvc.perform(post("/api/v1/goals/{goalId}/tasks", goal)
                        .header("Authorization", "Bearer " + token).header(CorsConfig.IDEMPOTENCY_KEY_HEADER, "goal-task-create")
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("문제 풀기", "다른 행동")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(10004));
    }

    @Test
    @DisplayName("일반 Task 생성·수정에서 목표를 선택하고 생략 시 유지하며 명시적으로 해제한다")
    void normalTaskCreateUpdateAndUnlink() throws Exception {
        String token = token("contribution-normal@example.com");
        Long firstGoal = goal(token);
        Long secondGoal = goal(token);
        String response = mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"일회성\",\"allDay\":false,\"linkedGoalId\":" + firstGoal + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.linkedGoalId").value(firstGoal))
                .andReturn().getResponse().getContentAsString();
        Long task = number(response, "$.data.id");
        mockMvc.perform(put("/api/v1/tasks/{id}", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정\",\"allDay\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.linkedGoalId").value(firstGoal));
        mockMvc.perform(put("/api/v1/tasks/{id}", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"다른 목표\",\"allDay\":false,\"linkedGoalId\":" + secondGoal + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.linkedGoalId").value(secondGoal));
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.linkedGoalId").isEmpty());
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.linkedGoalId").isEmpty());
    }

    @Test
    @DisplayName("완료된 기존 Task의 목표 연결·변경·해제는 완료 시각을 유지하고 직접 기록을 복제하지 않는다")
    void completedTaskLinkDoesNotDuplicateLogs() throws Exception {
        String token = token("contribution-link@example.com");
        Long first = goal(token);
        Long second = goal(token);
        Long task = task(token);
        String done = mockMvc.perform(patch("/api/v1/tasks/{id}/done", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Object completedAt = JsonPath.read(done, "$.data.completedAt");
        for (Long goal : List.of(first, second)) {
            mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.linkedGoalId").value(goal))
                    .andExpect(jsonPath("$.data.status").value("DONE"))
                    .andExpect(jsonPath("$.data.completedAt").value(completedAt));
        }
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedAt").value(completedAt));
        assertEquals(0, logRepository.findAll().stream()
                .filter(log -> List.of(first, second).contains(log.getGoal().getId())).count());
    }

    @Test
    @DisplayName("다른 사용자 목표·Task에는 연결하거나 생성할 수 없다")
    void taskOwnership() throws Exception {
        String owner = token("contribution-task-owner@example.com");
        String other = token("contribution-task-other@example.com");
        Long goal = goal(owner);
        Long task = task(owner);
        Long otherTask = task(other);
        mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(20001));
        mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", otherTask).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/goals/{id}/tasks", goal).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"불가\",\"allDay\":false,\"date\":\"" + today() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"불가\",\"allDay\":false,\"linkedGoalId\":" + goal + "}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("반복 회차 Task의 목표는 개별 Task API로 연결하거나 해제할 수 없다")
    void occurrenceTaskLinkProtected() throws Exception {
        String token = token("contribution-occurrence@example.com");
        Long goal = goal(token);
        var plan = new GoalPlanCreateRequest(goal, "계획", null,
                new GoalPlanScheduleRequest(today(), List.of(ScheduleDay.values()), "Asia/Seoul", null),
                new GoalPlanActionRequest("기본", null, null), new GoalPlanActionRequest("최소", null, null), null);
        mockMvc.perform(post("/api/v1/goal-plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isCreated());
        String occurrences = mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long task = number(occurrences, "$.data[0].linkedTaskId");
        mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(70005));
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/tasks/{id}", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"불가\",\"allDay\":false,\"linkedGoalId\":" + goal + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("직접 기록 생성·수정·취소는 원본을 유지하고 생성 재시도는 취소 기록을 되살리지 않는다")
    void directLogCrudAndDurableIdempotency() throws Exception {
        String token = token("contribution-log-crud@example.com");
        Long goal = goal(token);
        var original = logRequest("원래 기록");
        String response = createLog(token, goal, "log-crud", original).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.goalId").value(goal)).andExpect(jsonPath("$.data.goalVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        Long id = number(response, "$.data.id");
        mockMvc.perform(patch("/api/v1/goal-direct-logs/{id}", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(logRequest("수정 기록"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("수정 기록"));
        createLog(token, goal, "log-crud", original).andExpect(status().isCreated())
                .andExpect(header().string(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.data.id").value(id)).andExpect(jsonPath("$.data.title").value("수정 기록"));
        for (int retry = 0; retry < 2; retry++) {
            mockMvc.perform(delete("/api/v1/goal-direct-logs/{id}", id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        createLog(token, goal, "log-crud", original).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(id)).andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
        createLog(token, goal, "log-crud", logRequest("다른 생성"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(10004));
        mockMvc.perform(patch("/api/v1/goal-direct-logs/{id}", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(original)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(70007));
    }

    @Test
    @DisplayName("직접 기록의 key는 목표별로 분리하고 타 사용자는 기록에 접근할 수 없다")
    void directLogScopeAndOwnership() throws Exception {
        String owner = token("contribution-log-owner@example.com");
        String other = token("contribution-log-other@example.com");
        Long goal = goal(owner);
        Long another = goal(owner);
        String first = createLog(owner, goal, "same-key", logRequest("실천")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = createLog(owner, another, "same-key", logRequest("실천")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(number(first, "$.data.id"), number(second, "$.data.id"));
        Long logId = number(first, "$.data.id");
        createLog(other, goal, "other", logRequest("불가")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/goal-direct-logs/{id}", logId).header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(logRequest("불가"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70006));
        mockMvc.perform(delete("/api/v1/goal-direct-logs/{id}", logId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("직접 기록은 필수 idempotency key와 timezone·날짜·제목을 검증한다")
    void directLogValidation() throws Exception {
        String token = token("contribution-log-validation@example.com");
        Long goal = goal(token);
        String body = objectMapper.writeValueAsString(logRequest("실천"));
        mockMvc.perform(post("/api/v1/goals/{id}/direct-logs", goal).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        for (String key : List.of(" ", "x".repeat(161))) {
            createLog(token, goal, key, logRequest("실천")).andExpect(status().isBadRequest());
        }
        createLog(token, goal, "bad-zone", new GoalDirectLogRequest(today(), "Wrong/Zone", "실천", null))
                .andExpect(status().isBadRequest());
        createLog(token, goal, "bad-date", new GoalDirectLogRequest(null, null, "실천", null))
                .andExpect(status().isBadRequest());
        createLog(token, goal, "bad-title", new GoalDirectLogRequest(today(), null, " ", null))
                .andExpect(status().isBadRequest());
        createLog(token, goal, "long-note", new GoalDirectLogRequest(today(), null, "실천", "x".repeat(1001)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/goal-direct-logs/{id}", Long.MAX_VALUE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("직접 기록 생성은 정규화한 동일 본문을 재사용하고 동시에 생성해도 하나만 저장한다")
    void concurrentDirectLogCreation() throws Exception {
        String token = token("contribution-log-concurrent@example.com");
        User owner = userRepository.findByEmail("contribution-log-concurrent@example.com").orElseThrow();
        Long goal = goal(token);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<GoalDirectLogService.CreateResult> call = () -> {
                start.await();
                return logService.create(goal, "concurrent-key", logRequest("실천"), owner);
            };
            var a = executor.submit(call);
            var b = executor.submit(call);
            start.countDown();
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertEquals(first.response().id(), second.response().id());
            assertNotEquals(first.replayed(), second.replayed());
        }
        createLog(token, goal, "concurrent-key", new GoalDirectLogRequest(today(), " Asia/Seoul ", " 실천 ", " "))
                .andExpect(status().isCreated()).andExpect(header().string(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER, "true"));
    }


    @Test
    @DisplayName("직접 실천 기록은 반복 회차를 자동 완료하지 않는다")
    void directLogDoesNotCompleteOccurrence() throws Exception {
        String token = token("contribution-direct-independent@example.com");
        Long goal = goal(token);
        var plan = new GoalPlanCreateRequest(goal, "계획", null,
                new GoalPlanScheduleRequest(today(), List.of(ScheduleDay.values()), "Asia/Seoul", null),
                new GoalPlanActionRequest("기본", null, null), new GoalPlanActionRequest("최소", null, null), null);
        mockMvc.perform(post("/api/v1/goal-plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isCreated());
        createLog(token, goal, "independent", logRequest("추가 실천")).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].result").isEmpty());
    }

    @Test
    @DisplayName("일반 반복 Task와 workspace 요청에는 개인 목표 연결을 허용하지 않는다")
    void recurringAndWorkspaceTaskRejected() throws Exception {
        String token = token("contribution-recurrence@example.com");
        Long goal = goal(token);
        String body = "{\"title\":\"반복\",\"allDay\":false,\"startAt\":\"" + today()
                + "T09:00:00\",\"recurrence\":{\"frequency\":\"DAILY\",\"recurrenceCount\":2},\"linkedGoalId\":" + goal + "}";
        mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        String workspace = mockMvc.perform(post("/api/v1/workspaces").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"공유\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/workspaces/{id}/tasks", number(workspace, "$.data.id"))
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"공유 Task\",\"allDay\":false,\"linkedGoalId\":" + goal + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Task 완료와 목표 연결을 동시에 요청해도 완료 상태와 목표 연결이 모두 유지된다")
    void concurrentTaskCompletionAndGoalLink() throws Exception {
        String token = token("contribution-link-concurrent@example.com");
        User owner = userRepository.findByEmail("contribution-link-concurrent@example.com").orElseThrow();
        Long goal = goal(token);
        Long task = task(token);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var link = executor.submit(() -> {
                start.await();
                return taskLinkService.link(task, goal, owner);
            });
            var complete = executor.submit(() -> {
                start.await();
                return taskTxService.completeTxForOwner(task, java.time.LocalDateTime.now(Constant.ZONE), owner);
            });
            start.countDown();
            link.get(20, TimeUnit.SECONDS);
            complete.get(20, TimeUnit.SECONDS);
        }
        mockMvc.perform(get("/api/v1/tasks/{id}", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.linkedGoalId").value(goal));
    }


    @Test
    @DisplayName("목표 연결 변경은 기존 D-Day 연결을 유지하고 key는 대소문자를 구분한다")
    void ddayConnectionAndCaseSensitiveKeys() throws Exception {
        String token = token("contribution-dday@example.com");
        Long goal = goal(token);
        Long task = task(token);
        String dday = mockMvc.perform(post("/api/v1/dday-goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"시험\",\"targetDate\":\"" + today() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long ddayId = number(dday, "$.data.id");
        mockMvc.perform(patch("/api/v1/tasks/{id}/dday-goal", task).header("Authorization", "Bearer " + token)
                        .param("ddayGoalId", ddayId.toString())).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + goal + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ddayGoalId").value(ddayId));
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ddayGoalId").value(ddayId));
        String upper = createLog(token, goal, "Key", logRequest("실천")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String lower = createLog(token, goal, "key", logRequest("실천")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(number(upper, "$.data.id"), number(lower, "$.data.id"));
    }

    private ResultActions createLog(String token, Long goal, String key, GoalDirectLogRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/goals/{id}/direct-logs", goal).header("Authorization", "Bearer " + token)
                .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, key).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private Long goal(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new GoalCreateRequest("목표", null, null, null, null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(response, "$.data.id");
    }

    private Long task(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tasks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"기존 Task\",\"allDay\":false}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(response, "$.data.id");
    }

    private GoalDirectLogRequest logRequest(String title) {
        return new GoalDirectLogRequest(today(), "Asia/Seoul", title, null);
    }

    private LocalDate today() { return LocalDate.now(Constant.ZONE); }

    private Long number(String response, String path) { return ((Number) JsonPath.read(response, path)).longValue(); }

    private String token(String email) {
        return tokenService.createAccessToken(userRepository.save(new User(email, "encoded", "목표 실천 사용자"))).tokenValue();
    }
}
