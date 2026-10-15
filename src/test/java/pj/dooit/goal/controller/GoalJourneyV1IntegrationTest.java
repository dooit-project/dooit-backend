package pj.dooit.goal.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.Constant;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.config.CorsConfig;
import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalDirectLogRequest;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalPlanUpdateRequest;
import pj.dooit.goal.dto.GoalPracticeResultRequest;
import pj.dooit.goal.dto.GoalUpdateRequest;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.mail.MailService;
import pj.dooit.task.repository.TaskRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalJourneyV1IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired TaskRepository taskRepository;
    @Autowired GoalPlanOccurrenceRepository occurrenceRepository;
    @MockitoBean MailService mailService;

    @Test
    @DisplayName("세 원본을 중복 없이 페이지로 조회하고 summary는 모든 페이지에서 전체 기간을 집계한다")
    void mixedSourcesAndFullRangeSummary() throws Exception {
        String token = token("journey-mixed@example.com");
        Long goal = goal(token);
        LocalDate date = today();
        plan(token, goal, date.minusDays(2));
        String occurrences = occurrences(token, date.minusDays(2), date.plusDays(2));
        Long occurrence = number(occurrences, "$.data[2].id");
        Long linkedTask = number(occurrences, "$.data[2].linkedTaskId");
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", number(occurrences, "$.data[1].id"))
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        practice(token, occurrence, ActionVariant.MINIMUM, date.atTime(9, 0), "Asia/Seoul", "최소 실천");
        // Legacy data can contain a redundant Task goal link; the occurrence still takes precedence.
        var legacyTask = taskRepository.findById(linkedTask).orElseThrow();
        legacyTask.connectGoal(goal);
        taskRepository.save(legacyTask);
        Long oneOff = completedTask(token, goal, date.atTime(11, 0));
        Long firstLog = directLog(token, goal, date, "첫 기록");
        Long secondLog = directLog(token, goal, date, "둘째 기록");
        long tasksBefore = taskRepository.count();
        long occurrencesBefore = occurrenceRepository.count();
        String cursor = null;
        String summary = null;
        String version = null;
        List<String> sourceTypes = new ArrayList<>();
        List<Long> sourceIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            String response = journey(token, goal, date.minusDays(2), date.plusDays(2), "Asia/Seoul", cursor, 1)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                    .andExpect(jsonPath("$.data.summary.practiceOccurrenceCount").value(1))
                    .andExpect(jsonPath("$.data.summary.oneOffCompletionCount").value(1))
                    .andExpect(jsonPath("$.data.summary.directLogCount").value(2))
                    .andExpect(jsonPath("$.data.summary.practiceDateCount").value(1))
                    .andExpect(jsonPath("$.data.summary.standardCount").value(0))
                    .andExpect(jsonPath("$.data.summary.minimumCount").value(1))
                    .andExpect(jsonPath("$.data.summary.unknownCount").value(3))
                    .andExpect(jsonPath("$.data.summary.restCount").value(1))
                    .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(3))
                    .andReturn().getResponse().getContentAsString();
            String actualSummary = objectMapper.writeValueAsString((Object) JsonPath.read(response, "$.data.summary"));
            String actualVersion = JsonPath.read(response, "$.data.journeyVersion");
            if (summary != null) {
                assertEquals(summary, actualSummary);
                assertEquals(version, actualVersion);
            }
            summary = actualSummary;
            version = actualVersion;
            String type = JsonPath.read(response, "$.data.items[0].sourceType");
            Long id = number(response, "$.data.items[0].sourceId");
            assertTrue(seen.add(type + ":" + id));
            sourceTypes.add(type);
            sourceIds.add(id);
            cursor = JsonPath.read(response, "$.data.nextCursor");
        }
        assertNull(cursor);
        assertEquals(List.of("DIRECT_LOG", "DIRECT_LOG", "PLAN_OCCURRENCE", "TASK_COMPLETION"), sourceTypes);
        assertEquals(List.of(secondLog, firstLog, occurrence, oneOff), sourceIds);
        assertEquals(tasksBefore, taskRepository.count());
        assertEquals(occurrencesBefore, occurrenceRepository.count());
    }

    @Test
    @DisplayName("장기간 여정 조회는 회차나 Task를 생성하지 않고 미생성 날짜를 미실천으로 추정하지 않는다")
    void longRangeReadDoesNotMaterialize() throws Exception {
        String token = token("journey-empty@example.com");
        Long goal = goal(token);
        plan(token, goal, today().minusDays(400));
        long tasks = taskRepository.count();
        long occurrences = occurrenceRepository.count();
        String first = journey(token, goal, today().minusDays(400), today().plusDays(30), null, null, 50)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(0))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty()).andReturn().getResponse().getContentAsString();
        String second = journey(token, goal, today().minusDays(400), today().plusDays(30), null, null, 1)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals((String) JsonPath.read(first, "$.data.journeyVersion"), JsonPath.read(second, "$.data.journeyVersion"));
        assertEquals(tasks, taskRepository.count());
        assertEquals(occurrences, occurrenceRepository.count());
    }

    @Test
    @DisplayName("시각 있는 실천과 Task는 요청 timezone으로 날짜를 변환하고 직접 기록 날짜는 보존한다")
    void timeZoneAndActualPracticeDate() throws Exception {
        String token = token("journey-zone@example.com");
        Long goal = goal(token);
        LocalDate date = today();
        plan(token, goal, date.minusDays(3));
        String occurrence = occurrences(token, date.minusDays(3), date.minusDays(3));
        practice(token, number(occurrence, "$.data[0].id"), ActionVariant.STANDARD,
                date.atTime(0, 30), "Asia/Tokyo", null);
        completedTask(token, goal, date.atTime(0, 15));
        directLog(token, goal, date, "날짜만 있는 기록");
        journey(token, goal, date.minusDays(1), date.minusDays(1), "America/Los_Angeles", null, 50)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.summary.practiceOccurrenceCount").value(1))
                .andExpect(jsonPath("$.data.summary.oneOffCompletionCount").value(1))
                .andExpect(jsonPath("$.data.summary.directLogCount").value(0))
                .andExpect(jsonPath("$.data.items[0].practicedDate").value(date.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.items[0].serviceDate").value(date.minusDays(3).toString()));
        journey(token, goal, date.minusDays(1), date, "America/Los_Angeles", null, 50)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.summary.practiceDateCount").value(2));
        journey(token, goal, date, date, "America/Los_Angeles", null, 50)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("DIRECT_LOG"))
                .andExpect(jsonPath("$.data.items[0].practicedDate").value(date.toString()));
    }

    @Test
    @DisplayName("실천 취소·Task 재개·직접 기록 취소가 여정 원본과 집계 및 version에 반영된다")
    void cancellationConsistency() throws Exception {
        String token = token("journey-cancel@example.com");
        Long goal = goal(token);
        plan(token, goal, today());
        Long occurrence = number(occurrences(token, today(), today()), "$.data[0].id");
        practice(token, occurrence, ActionVariant.STANDARD, today().atTime(9, 0), "Asia/Seoul", null);
        Long task = completedTask(token, goal, today().atTime(10, 0));
        Long log = directLog(token, goal, today(), "기록");
        String before = journey(token, goal, today(), today(), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3)).andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done/cancel", task).header("Authorization", "Bearer " + token)
                        .param("date", today().toString())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/goal-direct-logs/{id}", log).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String after = journey(token, goal, today(), today(), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.summary.practiceOccurrenceCount").value(0))
                .andExpect(jsonPath("$.data.summary.oneOffCompletionCount").value(0))
                .andExpect(jsonPath("$.data.summary.directLogCount").value(0))
                .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertNotEquals((String) JsonPath.read(before, "$.data.journeyVersion"), JsonPath.read(after, "$.data.journeyVersion"));
    }

    @Test
    @DisplayName("기존 완료 Task의 목표 연결 해제·변경은 목표별 여정에서 중복 없이 이동한다")
    void taskGoalLinkChanges() throws Exception {
        String token = token("journey-link@example.com");
        Long original = goal(token);
        Long other = goal(token);
        Long task = completedTask(token, original, today().atTime(10, 0));
        mockMvc.perform(delete("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        journey(token, original, today(), today(), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.oneOffCompletionCount").value(0));
        mockMvc.perform(put("/api/v1/tasks/{id}/goal-link", task).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goalId\":" + other + "}"))
                .andExpect(status().isOk());
        journey(token, other, today(), today(), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.oneOffCompletionCount").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceId").value(task));
    }

    @Test
    @DisplayName("계획과 목표 변경 후에도 과거 실천의 목표·계획·행동 snapshot은 보존된다")
    void historicalSnapshots() throws Exception {
        String token = token("journey-snapshot@example.com");
        Long original = goal(token);
        Long other = goal(token);
        LocalDate past = today().minusDays(1);
        Long plan = plan(token, original, past);
        Long occurrence = number(occurrences(token, past, past), "$.data[0].id");
        practice(token, occurrence, ActionVariant.STANDARD, past.atTime(9, 0), "Asia/Seoul", "과거 실천");
        directLog(token, original, past, "과거 직접 기록");
        mockMvc.perform(patch("/api/v1/goals/{id}", original).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new GoalUpdateRequest("새 목표 이름", null, null, null, null, today()))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/goal-plans/{id}", plan).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new GoalPlanUpdateRequest(other, "새 계획", null,
                                        new GoalPlanScheduleRequest(past, List.of(ScheduleDay.values()), "Asia/Seoul", null),
                                        new GoalPlanActionRequest("새 기본", null, null), new GoalPlanActionRequest("새 최소", null, null),
                                        "새 다음 행동", today()))))
                .andExpect(status().isOk());
        journey(token, original, past, past, null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].snapshotGoalTitle").value("원래 목표"))
                .andExpect(jsonPath("$.data.items[1].snapshotGoalTitle").value("원래 목표"))
                .andExpect(jsonPath("$.data.items[1].title").value("원래 계획"))
                .andExpect(jsonPath("$.data.items[1].actionText").value("기본 행동"))
                .andExpect(jsonPath("$.data.items[1].goalVersion").value(1))
                .andExpect(jsonPath("$.data.items[1].planVersion").value(1));
        journey(token, other, past, past, null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("페이지 사이 기록 수정은 summary 수가 같아도 cursor를 무효화한다")
    void staleCursorAfterEdit() throws Exception {
        String token = token("journey-stale@example.com");
        Long goal = goal(token);
        Long first = directLog(token, goal, today(), "첫 기록");
        directLog(token, goal, today(), "둘째 기록");
        String initial = journey(token, goal, today(), today(), null, null, 1).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(initial, "$.data.nextCursor");
        mockMvc.perform(patch("/api/v1/goal-direct-logs/{id}", first).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new GoalDirectLogRequest(today(), "Asia/Seoul", "첫 기록 수정", "새 메모"))))
                .andExpect(status().isOk());
        journey(token, goal, today(), today(), null, cursor, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(70008));
        String refreshed = journey(token, goal, today(), today(), null, null, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.directLogCount").value(2)).andReturn().getResponse().getContentAsString();
        assertNotEquals((String) JsonPath.read(initial, "$.data.journeyVersion"), JsonPath.read(refreshed, "$.data.journeyVersion"));
    }

    @Test
    @DisplayName("다른 목표·기간·timezone cursor와 손상된 cursor를 거절하고 타 사용자 목표는 숨긴다")
    void cursorValidationAndOwnerScope() throws Exception {
        String token = token("journey-owner@example.com");
        String other = token("journey-other@example.com");
        Long goal = goal(token);
        Long another = goal(token);
        directLog(token, goal, today(), "하나");
        directLog(token, goal, today(), "둘");
        String response = journey(token, goal, today(), today(), null, null, 1).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String cursor = JsonPath.read(response, "$.data.nextCursor");
        journey(other, goal, today(), today(), null, cursor, 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(70001));
        journey(token, another, today(), today(), null, cursor, 1).andExpect(status().isBadRequest());
        journey(token, goal, today().minusDays(1), today(), null, cursor, 1).andExpect(status().isBadRequest());
        journey(token, goal, today(), today(), "UTC", cursor, 1).andExpect(status().isBadRequest());
        for (String invalid : List.of("%%%", "x".repeat(2049))) {
            journey(token, goal, today(), today(), null, invalid, 1).andExpect(status().isBadRequest());
        }
        String payload = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
        String invalidId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (payload.substring(0, payload.lastIndexOf('|') + 1) + Long.MAX_VALUE).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        journey(token, goal, today(), today(), null, invalidId, 1).andExpect(status().isBadRequest());
        String[] fields = payload.split("\\|", -1);
        fields[1] = "invalid-version";
        String invalidVersion = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.join("|", fields).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        journey(token, goal, today(), today(), null, invalidVersion, 1).andExpect(status().isBadRequest());
        journey(token, goal, today(), today(), "Wrong/Zone", null, 1).andExpect(status().isBadRequest());
        journey(token, goal, today(), today().minusDays(1), null, null, 1).andExpect(status().isBadRequest());
        for (int limit : List.of(0, 101)) {
            journey(token, goal, today(), today(), null, null, limit).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/goals/{id}/journey", goal).param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/goals/{id}/journey", goal).header("Authorization", "Bearer " + token)
                        .param("to", today().toString())).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정지 계획의 숨겨진 미래 미실천 회차는 미기록 집계에서 제외하고 과거 미기록은 유지한다")
    void pausedFuturePendingExcluded() throws Exception {
        String token = token("journey-paused@example.com");
        Long goal = goal(token);
        Long plan = plan(token, goal, today().minusDays(1));
        occurrences(token, today().minusDays(1), today().plusDays(1));
        journey(token, goal, today().minusDays(1), today().plusDays(1), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(3));
        mockMvc.perform(patch("/api/v1/goal-plans/{id}/status", plan).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk());
        journey(token, goal, today().minusDays(1), today().plusDays(1), null, null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(1));
    }


    @Test
    @DisplayName("날짜가 이틀 달라지는 timezone의 실천도 요청 날짜 범위에 포함한다")
    void twoDateTimeZoneDifference() throws Exception {
        String token = token("journey-two-date@example.com");
        Long goal = goal(token);
        plan(token, goal, today().minusDays(3));
        Long occurrence = number(occurrences(token, today().minusDays(3), today().minusDays(3)), "$.data[0].id");
        practice(token, occurrence, ActionVariant.MINIMUM, today().minusDays(2).atTime(23, 30), "Pacific/Pago_Pago", null);
        journey(token, goal, today(), today(), "Pacific/Kiritimati", null, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].practicedDate").value(today().toString()))
                .andExpect(jsonPath("$.data.summary.minimumCount").value(1));
    }

    private ResultActions journey(String token, Long goal, LocalDate from, LocalDate to, String zone, String cursor, int limit) throws Exception {
        var request = get("/api/v1/goals/{id}/journey", goal).header("Authorization", "Bearer " + token)
                .param("from", from.toString()).param("to", to.toString()).param("limit", "" + limit);
        if (zone != null) request.param("timeZone", zone);
        if (cursor != null) request.param("cursor", cursor);
        return mockMvc.perform(request);
    }

    private Long goal(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new GoalCreateRequest("원래 목표", null, null, null, null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(response, "$.data.id");
    }

    private Long plan(String token, Long goal, LocalDate start) throws Exception {
        var request = new GoalPlanCreateRequest(goal, "원래 계획", null,
                new GoalPlanScheduleRequest(start, List.of(ScheduleDay.values()), "Asia/Seoul", null),
                new GoalPlanActionRequest("기본 행동", null, null), new GoalPlanActionRequest("최소 행동", null, null), "다음 행동");
        String response = mockMvc.perform(post("/api/v1/goal-plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(response, "$.data.id");
    }

    private String occurrences(String token, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void practice(String token, Long occurrence, ActionVariant variant, LocalDateTime at, String zone, String note) throws Exception {
        var request = new GoalPracticeResultRequest(variant, at, zone, note, List.of(), NextActionPatchKind.KEEP, null, null);
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/result", occurrence).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Long completedTask(String token, Long goal, LocalDateTime at) throws Exception {
        String response = mockMvc.perform(post("/api/v1/goals/{id}/tasks", goal).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"일회성 실천\",\"date\":\"" + today() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long task = number(response, "$.data.id");
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", task).header("Authorization", "Bearer " + token)
                        .param("completedAt", at.toString())).andExpect(status().isOk());
        return task;
    }

    private Long directLog(String token, Long goal, LocalDate date, String title) throws Exception {
        String response = mockMvc.perform(post("/api/v1/goals/{id}/direct-logs", goal).header("Authorization", "Bearer " + token)
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, title).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoalDirectLogRequest(date, "Asia/Seoul", title, null))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(response, "$.data.id");
    }

    private LocalDate today() { return LocalDate.now(Constant.ZONE); }
    private Long number(String response, String path) { return ((Number) JsonPath.read(response, path)).longValue(); }
    private String token(String email) {
        return tokenService.createAccessToken(userRepository.save(new User(email, "encoded", "여정 사용자"))).tokenValue();
    }
}
