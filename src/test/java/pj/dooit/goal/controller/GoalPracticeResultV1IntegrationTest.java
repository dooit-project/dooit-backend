package pj.dooit.goal.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.Constant;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.goal.domain.ActionVariant;
import pj.dooit.goal.domain.NextActionPatchKind;
import pj.dooit.goal.domain.PracticeDifficulty;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.dto.GoalPracticeResultRequest;
import pj.dooit.goal.exception.GoalOccurrenceConflictException;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.goal.service.GoalPracticeResultService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalPracticeResultV1IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired GoalPracticeResultRepository resultRepository;
    @Autowired GoalPracticeResultService resultService;
    @Autowired pj.dooit.task.service.TaskTxService taskTxService;
    @MockitoBean MailService mailService;

    @Test
    @DisplayName("기본·최소 실천 저장·수정·취소는 하나의 결과와 연결 Task 상태를 함께 갱신한다")
    void resultCrudAndTaskSynchronization() throws Exception {
        String token = token("practice-crud@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        assertNotNull(occurrence.taskId());
        putResult(token, occurrence, ActionVariant.STANDARD, occurrence.version())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionVariant").value("STANDARD"));
        assertTask(token, occurrence.taskId(), "DONE");
        Occurrence practiced = occurrence(token, occurrence.date());
        assertEquals("PRACTICED", practiced.status());
        assertEquals("STANDARD", practiced.variant());
        putResult(token, occurrence, ActionVariant.MINIMUM, practiced.version())
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionVariant").value("MINIMUM"));
        assertEquals(1, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        Occurrence edited = occurrence(token, occurrence.date());
        assertEquals("MINIMUM", edited.variant());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence.id())
                        .header("Authorization", "Bearer " + token).param("occurrenceVersion", "" + edited.version()))
                .andExpect(status().isOk());
        assertEquals("PENDING", occurrence(token, occurrence.date()).status());
        assertTask(token, occurrence.taskId(), "TODAY");
        assertEquals(0, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("휴식과 실천은 명시적 취소 후 전환하고 휴식 취소는 Task를 재개한다")
    void restTransitionsAndRollback() throws Exception {
        String token = token("practice-rest@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESTED"))
                .andExpect(jsonPath("$.data.scheduledNotificationAt").isEmpty());
        assertTask(token, occurrence.taskId(), "INBOX");
        putResult(token, occurrence, ActionVariant.MINIMUM, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(70004));
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrence.taskId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertTask(token, occurrence.taskId(), "INBOX");
        assertEquals(0, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertTask(token, occurrence.taskId(), "TODAY");
        putResult(token, occurrence, ActionVariant.MINIMUM, null).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        assertEquals("PRACTICED", occurrence(token, occurrence.date()).status());
        assertTask(token, occurrence.taskId(), "DONE");
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("PRACTICED", occurrence(token, occurrence.date()).status());
    }

    @Test
    @DisplayName("Task 완료는 UNKNOWN 결과로 수렴하고 반복 완료는 명시적 실천을 보존하며 재개는 결과를 취소한다")
    void taskCompletionAndReopen() throws Exception {
        String token = token("practice-task@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrence.taskId())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("UNKNOWN", occurrence(token, occurrence.date()).variant());
        putResult(token, occurrence, ActionVariant.MINIMUM, null).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrence.taskId())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("MINIMUM", occurrence(token, occurrence.date()).variant());
        assertEquals(1, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done/cancel", occurrence.taskId())
                        .header("Authorization", "Bearer " + token).param("date", occurrence.date().toString()))
                .andExpect(status().isOk());
        assertEquals("PENDING", occurrence(token, occurrence.date()).status());
        assertEquals(0, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
    }

    @Test
    @DisplayName("결과 수정도 occurrenceVersion을 올리고 오래된 실천·휴식 요청은 충돌한다")
    void staleVersionRejected() throws Exception {
        String token = token("practice-stale@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        putResult(token, occurrence, ActionVariant.STANDARD, occurrence.version()).andExpect(status().isOk());
        putResult(token, occurrence, ActionVariant.MINIMUM, occurrence.version()).andExpect(status().isConflict());
        Occurrence practiced = occurrence(token, occurrence.date());
        putResult(token, occurrence, ActionVariant.MINIMUM, practiced.version()).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence.id())
                        .header("Authorization", "Bearer " + token).param("occurrenceVersion", "" + practiced.version()))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token).param("occurrenceVersion", "" + occurrence.version()))
                .andExpect(status().isConflict());
        assertEquals("MINIMUM", occurrence(token, occurrence.date()).variant());
    }

    @Test
    @DisplayName("타 사용자 회차 결과와 휴식은 조회·변경할 수 없다")
    void ownershipAndAuthentication() throws Exception {
        String owner = token("practice-owner@example.com");
        String other = token("practice-other@example.com");
        Occurrence occurrence = createOccurrence(owner, LocalDate.now(Constant.ZONE));
        putResult(other, occurrence, ActionVariant.STANDARD, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70003));
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence.id())
                        .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrence.taskId())
                        .header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())).andExpect(status().isUnauthorized());
        assertEquals("PENDING", occurrence(owner, occurrence.date()).status());
    }

    @Test
    @DisplayName("잘못된 timezone·다음 행동 요청은 저장하지 않고 실천 timezone은 Task 완료 시각에 변환된다")
    void validationAndTimeZone() throws Exception {
        String token = token("practice-zone@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        var invalid = request(ActionVariant.MINIMUM, "Invalid/Zone", NextActionPatchKind.KEEP, null, null);
        performResult(token, occurrence.id(), invalid).andExpect(status().isBadRequest());
        performResult(token, occurrence.id(), request(ActionVariant.MINIMUM, null, NextActionPatchKind.REPLACE, " ", null))
                .andExpect(status().isBadRequest());
        performResult(token, occurrence.id(), request(ActionVariant.MINIMUM, null, NextActionPatchKind.CLEAR, "내용", null))
                .andExpect(status().isBadRequest());
        assertEquals("PENDING", occurrence(token, occurrence.date()).status());
        assertTask(token, occurrence.taskId(), "TODAY");
        var valid = request(ActionVariant.MINIMUM, "America/Los_Angeles", NextActionPatchKind.REPLACE, "다음 장 열기", null);
        performResult(token, occurrence.id(), valid).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextAction").value("다음 장 열기"))
                .andExpect(jsonPath("$.data.difficulties.length()").value(1));
        LocalDateTime completedAt = valid.practicedAt().atZone(ZoneId.of(valid.practicedTimeZone()))
                .withZoneSameInstant(Constant.ZONE).toLocalDateTime();
        mockMvc.perform(get("/api/v1/tasks/{id}", occurrence.taskId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.completedAt").value(completedAt.toString() + ":00"));
    }

    @Test
    @DisplayName("과거 회차는 Task를 만들지 않아도 실천 결과를 저장하고 취소할 수 있다")
    void historicalResultWithoutTask() throws Exception {
        String token = token("practice-past@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE).minusDays(2));
        assertEquals(null, occurrence.taskId());
        putResult(token, occurrence, ActionVariant.STANDARD, null).andExpect(status().isOk());
        assertEquals("PRACTICED", occurrence(token, occurrence.date()).status());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/result", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("PENDING", occurrence(token, occurrence.date()).status());
    }

    @Test
    @DisplayName("회차 연결 Task 삭제는 기록을 보존하기 위해 거절하고 계획 정지는 미래 Task를 Inbox로 이동한다")
    void deletionAndPause() throws Exception {
        String token = token("practice-delete@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        mockMvc.perform(delete("/api/v1/tasks/{id}", occurrence.taskId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(70004));
        mockMvc.perform(patch("/api/v1/goal-plans/{id}/status", occurrence.planId())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk());
        assertTask(token, occurrence.taskId(), "INBOX");
        mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", occurrence.date().toString()).param("to", occurrence.date().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("같은 버전의 동시 결과 변경은 하나만 성공하고 유효 결과를 하나로 유지한다")
    void concurrentResultUpdates() throws Exception {
        String token = token("practice-concurrent@example.com");
        User owner = userRepository.findByEmail("practice-concurrent@example.com").orElseThrow();
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> call = () -> {
                start.await();
                try {
                    resultService.putResult(occurrence.id(), request(ActionVariant.MINIMUM, null,
                            NextActionPatchKind.KEEP, null, occurrence.version()), owner);
                    return true;
                } catch (GoalOccurrenceConflictException e) {
                    return false;
                }
            };
            var a = executor.submit(call);
            var b = executor.submit(call);
            start.countDown();
            assertEquals(1, (a.get(20, TimeUnit.SECONDS) ? 1 : 0) + (b.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(1, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        assertEquals("MINIMUM", occurrence(token, occurrence.date()).variant());
        assertTask(token, occurrence.taskId(), "DONE");
    }


    @Test
    @DisplayName("Task 완료와 명시적 실천 저장의 동시 요청도 결과를 하나로 유지하고 완료 상태를 맞춘다")
    void concurrentTaskAndPractice() throws Exception {
        String token = token("practice-task-concurrent@example.com");
        User owner = userRepository.findByEmail("practice-task-concurrent@example.com").orElseThrow();
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var practice = executor.submit(() -> {
                start.await();
                return resultService.putResult(occurrence.id(), request(ActionVariant.MINIMUM, null,
                        NextActionPatchKind.KEEP, null, null), owner);
            });
            var task = executor.submit(() -> {
                start.await();
                return taskTxService.completeTxForOwner(occurrence.taskId(), LocalDateTime.now(Constant.ZONE), owner);
            });
            start.countDown();
            practice.get(20, TimeUnit.SECONDS);
            task.get(20, TimeUnit.SECONDS);
        }
        assertEquals(1, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
        assertEquals("MINIMUM", occurrence(token, occurrence.date()).variant());
        assertTask(token, occurrence.taskId(), "DONE");
    }

    @Test
    @DisplayName("null 어려움 목록 항목과 음수 version은 입력 오류로 거절한다")
    void invalidResultFields() throws Exception {
        String token = token("practice-invalid@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        var invalidDifficulties = new GoalPracticeResultRequest(ActionVariant.STANDARD,
                LocalDateTime.now(Constant.ZONE), null, null,
                java.util.Arrays.asList(PracticeDifficulty.NO_TIME, null), NextActionPatchKind.KEEP, null, null);
        performResult(token, occurrence.id(), invalidDifficulties).andExpect(status().isBadRequest());
        performResult(token, occurrence.id(), request(ActionVariant.MINIMUM, null, NextActionPatchKind.KEEP, null, -1L))
                .andExpect(status().isBadRequest());
        assertEquals("PENDING", occurrence(token, occurrence.date()).status());
    }


    @Test
    @DisplayName("정지 계획에서 Task 완료와 휴식을 취소해도 미래 Task는 Inbox에 유지한다")
    void pausedPlanCancellationsKeepTaskInInbox() throws Exception {
        String token = token("practice-paused-cancel@example.com");
        Occurrence occurrence = createOccurrence(token, LocalDate.now(Constant.ZONE));
        mockMvc.perform(patch("/api/v1/goal-plans/{id}/status", occurrence.planId())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrence.taskId())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done/cancel", occurrence.taskId())
                        .header("Authorization", "Bearer " + token).param("date", occurrence.date().toString()))
                .andExpect(status().isOk());
        assertTask(token, occurrence.taskId(), "INBOX");
        mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/goal-plan-occurrences/{id}/rest", occurrence.id())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertTask(token, occurrence.taskId(), "INBOX");
        assertEquals(0, resultRepository.findByOccurrenceIdIn(List.of(occurrence.id())).size());
    }

    private org.springframework.test.web.servlet.ResultActions putResult(String token, Occurrence occurrence,
                                                                       ActionVariant variant, Long version) throws Exception {
        return performResult(token, occurrence.id(), request(variant, null, NextActionPatchKind.KEEP, null, version));
    }

    private org.springframework.test.web.servlet.ResultActions performResult(String token, Long id,
                                                                            GoalPracticeResultRequest request) throws Exception {
        return mockMvc.perform(put("/api/v1/goal-plan-occurrences/{id}/result", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private GoalPracticeResultRequest request(ActionVariant variant, String zone, NextActionPatchKind patch, String next, Long version) {
        return new GoalPracticeResultRequest(variant, LocalDate.now(Constant.ZONE).atTime(9, 0), zone,
                "실천 메모", List.of(PracticeDifficulty.NO_TIME, PracticeDifficulty.NO_TIME), patch, next, version);
    }

    private void assertTask(String token, Long id, String status) throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value(status));
    }

    private Occurrence createOccurrence(String token, LocalDate date) throws Exception {
        var request = new GoalPlanCreateRequest(null, "실천 계획", null,
                new GoalPlanScheduleRequest(date, List.of(ScheduleDay.values()), "Asia/Seoul", null),
                new GoalPlanActionRequest("기본 실천", null, null), new GoalPlanActionRequest("최소 실천", null, null), null);
        mockMvc.perform(post("/api/v1/goal-plans").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        return occurrence(token, date);
    }

    private Occurrence occurrence(String token, LocalDate date) throws Exception {
        String response = mockMvc.perform(get("/api/v1/goal-plan-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", date.toString()).param("to", date.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Number task = JsonPath.read(response, "$.data[0].linkedTaskId");
        Number version = JsonPath.read(response, "$.data[0].occurrenceVersion");
        Number id = JsonPath.read(response, "$.data[0].id");
        Number plan = JsonPath.read(response, "$.data[0].planId");
        Object result = JsonPath.read(response, "$.data[0].result");
        return new Occurrence(id.longValue(), task == null ? null : task.longValue(), plan.longValue(), date,
                version.longValue(), JsonPath.read(response, "$.data[0].status"),
                result == null ? null : JsonPath.read(response, "$.data[0].result.actionVariant"));
    }

    private String token(String email) {
        User owner = userRepository.save(new User(email, "encoded", "실천 사용자"));
        return tokenService.createAccessToken(owner).tokenValue();
    }

    private record Occurrence(Long id, Long taskId, Long planId, LocalDate date, Long version, String status, String variant) {}
}
