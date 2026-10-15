package pj.dooit.personalsystem.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.Constant;
import pj.dooit.auth.service.GuestAccountCleanupService;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.config.CorsConfig;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.repository.GoalPlanRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPracticeResultRepository;
import pj.dooit.mail.MailService;
import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.PracticeDifficulty;
import pj.dooit.personalsystem.domain.ScheduleDay;
import pj.dooit.personalsystem.dto.PersonalSystemActionRequest;
import pj.dooit.personalsystem.dto.PersonalSystemCreateRequest;
import pj.dooit.personalsystem.dto.PersonalSystemScheduleRequest;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultRequest;
import pj.dooit.personalsystem.dto.PersonalSystemUpdateRequest;
import pj.dooit.personalsystem.repository.PersonalSystemRepository;
import pj.dooit.personalsystem.repository.PersonalSystemOccurrenceRepository;
import pj.dooit.personalsystem.service.PersonalSystemMigrationService;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:personal-system-migration;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalSystemMigrationV1IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokens;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PersonalSystemRepository systemRepository;
    @Autowired PersonalSystemOccurrenceRepository oldOccurrenceRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired GoalPlanRepository planRepository;
    @Autowired GoalPlanOccurrenceRepository occurrenceRepository;
    @Autowired GoalPracticeResultRepository resultRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired PersonalSystemMigrationService migrationService;
    @Autowired GuestAccountCleanupService cleanupService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean MailService mailService;

    @BeforeEach
    void enforceProductionMappingForeignKeys() {
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM ADD CONSTRAINT IF NOT EXISTS FK_PERSONAL_SYSTEM_MIGRATED_GOAL FOREIGN KEY (MIGRATED_GOAL_ID) REFERENCES GOAL(ID)");
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM ADD CONSTRAINT IF NOT EXISTS FK_PERSONAL_SYSTEM_MIGRATED_PLAN FOREIGN KEY (MIGRATED_PLAN_ID) REFERENCES GOAL_PLAN(ID)");
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM_OCCURRENCE ADD CONSTRAINT IF NOT EXISTS FK_PERSONAL_OCCURRENCE_MIGRATED FOREIGN KEY (MIGRATED_OCCURRENCE_ID) REFERENCES GOAL_PLAN_OCCURRENCE(ID)");
        jdbc.execute("ALTER TABLE GOAL_PLAN_OCCURRENCE ADD CONSTRAINT IF NOT EXISTS FK_GOAL_OCCURRENCE_TASK FOREIGN KEY (LINKED_TASK_ID) REFERENCES TASK(ID)");
        jdbc.execute("ALTER TABLE GOAL ADD CONSTRAINT IF NOT EXISTS FK_GOAL_DDAY FOREIGN KEY (LINKED_DDAY_GOAL_ID) REFERENCES DDAY_GOAL(ID)");
        jdbc.execute("ALTER TABLE TASK ADD CONSTRAINT IF NOT EXISTS FK_TASK_LINKED_GOAL FOREIGN KEY (LINKED_GOAL_ID) REFERENCES GOAL(ID)");
    }

    @Test
    @DisplayName("기존 버전·snapshot·실천·휴식과 Task·D-Day ID를 보존해 한 번 이관한다")
    void migrateHistoryAndReuseTasks() throws Exception {
        String token = token("migration-history@example.com");
        String dday = mockMvc.perform(post("/api/v1/dday-goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"시험\",\"targetDate\":\"" + today().plusDays(30) + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long ddayId = id(dday, "$.data.id");
        Long system = createSystem(token, "원래 방향", ddayId, today().minusDays(2));
        String old = oldOccurrences(token, today().minusDays(2), today().plusDays(1));
        Long oldFirst = id(old, "$.data[0].id");
        Long oldToday = id(old, "$.data[2].id");
        Long task = id(old, "$.data[2].linkedTaskId");
        practice(token, oldFirst, ActionVariant.STANDARD, today().minusDays(2));
        practice(token, oldToday, ActionVariant.MINIMUM, today());
        mockMvc.perform(put("/api/v1/personal-system-occurrences/{id}/rest", id(old, "$.data[1].id"))
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        String fullDirection = "a".repeat(119) + "😀" + " 새 방향 ".repeat(30);
        var updated = new PersonalSystemUpdateRequest(fullDirection, "새 이유", "저녁 식사 뒤",
                schedule(today().minusDays(2)), new PersonalSystemActionRequest("새 기본", "20개", 20),
                new PersonalSystemActionRequest("새 최소", "2개", 5), "새 다음 행동", ddayId, today().plusDays(1));
        mockMvc.perform(patch("/api/v1/personal-systems/{id}", system).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk());
        long tasksBefore = taskRepository.count();
        String migrated = migrate(token, system);
        Long goal = id(migrated, "$.data.goalId");
        Long plan = id(migrated, "$.data.planId");
        assertEquals(2, ((Number) JsonPath.read(migrated, "$.data.versionCount")).intValue());
        assertEquals(4, ((Number) JsonPath.read(migrated, "$.data.occurrenceCount")).intValue());
        assertEquals(2, ((Number) JsonPath.read(migrated, "$.data.resultCount")).intValue());
        assertEquals(tasksBefore, taskRepository.count());
        mockMvc.perform(get("/api/v1/goals/{id}", goal).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.desiredOutcome").value(fullDirection.trim()))
                .andExpect(jsonPath("$.data.title").value("a".repeat(119)))
                .andExpect(jsonPath("$.data.linkedDdayGoalId").value(ddayId));
        mockMvc.perform(get("/api/v1/goal-plans/{id}", plan).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentVersion").value(2))
                .andExpect(jsonPath("$.data.standardAction.targetAmount").value("20개"));
        mockMvc.perform(get("/api/v1/goals/{id}/journey", goal).header("Authorization", "Bearer " + token)
                        .param("from", today().minusDays(2).toString()).param("to", today().plusDays(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.practiceOccurrenceCount").value(2))
                .andExpect(jsonPath("$.data.summary.standardCount").value(1))
                .andExpect(jsonPath("$.data.summary.minimumCount").value(1))
                .andExpect(jsonPath("$.data.summary.restCount").value(1))
                .andExpect(jsonPath("$.data.summary.noRecordOccurrenceCount").value(1));
        Long newToday = oldOccurrenceRepository.findById(oldToday).orElseThrow().getMigratedOccurrenceId();
        var result = resultRepository.findById(newToday).orElseThrow();
        assertEquals("기존 실천 메모", result.getAccomplishedNote());
        assertEquals("Asia/Seoul", result.getPracticedTimeZone());
        assertEquals("REPLACE", result.getNextActionPatchKind().name());
        assertEquals("문제집 열기", result.getNextActionValue());
        assertEquals("NO_TIME", result.difficulties().getFirst().name());
        mockMvc.perform(get("/api/v1/tasks/{id}", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.notificationEnabled").value(false));
        mockMvc.perform(get("/api/v1/personal-systems/{id}", system).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.migratedGoalId").value(goal)).andExpect(jsonPath("$.data.migratedPlanId").value(plan));
        String retry = migrate(token, system);
        assertEquals(goal, id(retry, "$.data.goalId"));
        assertEquals(plan, id(retry, "$.data.planId"));
        assertEquals(true, (Boolean) JsonPath.read(retry, "$.data.alreadyMigrated"));
    }

    @Test
    @DisplayName("이관된 구 API 수정은 거절하고 Task 재개는 새 결과만 취소한다")
    void sourceFrozenAndTaskUpdatesCanonicalOnly() throws Exception {
        String token = token("migration-frozen@example.com");
        Long system = createSystem(token, "방향", null, today());
        String old = oldOccurrences(token, today(), today());
        Long occurrence = id(old, "$.data[0].id");
        Long task = id(old, "$.data[0].linkedTaskId");
        practice(token, occurrence, ActionVariant.MINIMUM, today());
        migrate(token, system);
        Long mapped = oldOccurrenceRepository.findById(occurrence).orElseThrow().getMigratedOccurrenceId();
        mockMvc.perform(patch("/api/v1/personal-systems/{id}/status", system).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(60004));
        var update = new PersonalSystemUpdateRequest("변경", null, "시작", schedule(today()),
                new PersonalSystemActionRequest("기본", null, null), new PersonalSystemActionRequest("최소", null, null), null, null, today());
        mockMvc.perform(patch("/api/v1/personal-systems/{id}", system).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isConflict());
        for (String suffix : List.of("result", "rest")) {
            mockMvc.perform(delete("/api/v1/personal-system-occurrences/{id}/" + suffix, occurrence)
                            .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        }
        mockMvc.perform(put("/api/v1/personal-system-occurrences/{id}/rest", occurrence)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/personal-system-occurrences/{id}/result", occurrence)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(practiceRequest(ActionVariant.STANDARD, today()))))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/tasks/{id}/done/cancel", task).header("Authorization", "Bearer " + token)
                        .param("date", today().toString())).andExpect(status().isOk());
        assertFalse(resultRepository.existsById(mapped));
        assertEquals("PENDING", occurrenceRepository.findById(mapped).orElseThrow().getStatus().name());
        String snapshot = oldOccurrences(token, today(), today());
        assertEquals("PRACTICED", JsonPath.read(snapshot, "$.data[0].status"));
        assertEquals(mapped, id(snapshot, "$.data[0].migratedOccurrenceId"));
        assertNull(JsonPath.read(snapshot, "$.data[0].scheduledNotificationAt"));
        mockMvc.perform(patch("/api/v1/tasks/{id}/done", task).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals("UNKNOWN", resultRepository.findById(mapped).orElseThrow().getActionVariant().name());
        assertEquals("MINIMUM", JsonPath.read(oldOccurrences(token, today(), today()), "$.data[0].result.actionVariant"));
    }

    @Test
    @DisplayName("같은 시스템 동시 이관은 하나의 Goal과 Plan을 생성한다")
    void concurrentMigrationIdempotency() throws Exception {
        String token = token("migration-concurrent@example.com");
        User owner = userRepository.findByEmail("migration-concurrent@example.com").orElseThrow();
        Long system = createSystem(token, "방향", null, today());
        long goals = goalRepository.count();
        long plans = planRepository.count();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<pj.dooit.personalsystem.dto.PersonalSystemMigrationResponse> call = () -> {
                start.await();
                return migrationService.migrate(system, owner);
            };
            var a = executor.submit(call);
            var b = executor.submit(call);
            start.countDown();
            var first = a.get(20, TimeUnit.SECONDS);
            var second = b.get(20, TimeUnit.SECONDS);
            assertEquals(first.goalId(), second.goalId());
            assertEquals(first.planId(), second.planId());
            assertNotEquals(first.alreadyMigrated(), second.alreadyMigrated());
        }
        assertEquals(goals + 1, goalRepository.count());
        assertEquals(plans + 1, planRepository.count());
    }

    @Test
    @DisplayName("다른 사용자 시스템 이관과 인증 없는 요청을 거절한다")
    void ownerScopeAndAuthentication() throws Exception {
        String token = token("migration-owner@example.com");
        String other = token("migration-other@example.com");
        Long system = createSystem(token, "방향", null, today());
        mockMvc.perform(post("/api/v1/personal-systems/{id}/goal-migration", system).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(60001));
        mockMvc.perform(post("/api/v1/personal-systems/{id}/goal-migration", system)).andExpect(status().isUnauthorized());
        assertNull(systemRepository.findById(system).orElseThrow().getMigratedPlanId());
    }

    @Test
    @DisplayName("결과가 누락된 회차는 부분 이관하지 않고 전체를 롤백한다")
    void invalidHistoryRollsBack() throws Exception {
        String token = token("migration-rollback@example.com");
        Long system = createSystem(token, "방향", null, today());
        Long occurrence = id(oldOccurrences(token, today(), today()), "$.data[0].id");
        jdbc.update("UPDATE PERSONAL_SYSTEM_OCCURRENCE SET STATUS = 'PRACTICED' WHERE ID = ?", occurrence);
        long goals = goalRepository.count();
        long plans = planRepository.count();
        mockMvc.perform(post("/api/v1/personal-systems/{id}/goal-migration", system).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        assertEquals(goals, goalRepository.count());
        assertEquals(plans, planRepository.count());
        assertNull(systemRepository.findById(system).orElseThrow().getMigratedPlanId());
        assertNull(oldOccurrenceRepository.findById(occurrence).orElseThrow().getMigratedOccurrenceId());
    }

    @Test
    @DisplayName("정지·보관된 원본은 새 목표와 계획에서도 같은 상태로 유지한다")
    void preservePausedAndArchivedStatus() throws Exception {
        for (String state : List.of("PAUSED", "ARCHIVED")) {
            String token = token("migration-state-" + state.toLowerCase() + "@example.com");
            Long system = createSystem(token, "방향", null, today());
            mockMvc.perform(patch("/api/v1/personal-systems/{id}/status", system).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + state + "\"}"))
                    .andExpect(status().isOk());
            String migrated = migrate(token, system);
            assertEquals(state, goalRepository.findById(id(migrated, "$.data.goalId")).orElseThrow().getStatus().name());
            assertEquals(state, planRepository.findById(id(migrated, "$.data.planId")).orElseThrow().getStatus().name());
        }
    }

    @Test
    @DisplayName("게스트 병합은 이관 mapping과 목표·계획·원본 이력의 owner를 같은 ID로 옮긴다")
    void guestMergePreservesPlanningGraph() throws Exception {
        User guest = userRepository.save(User.guest(LocalDateTime.now(Constant.ZONE).plusDays(31)));
        String token = tokens.createGuestAccessToken(guest).tokenValue();
        Long system = createSystem(token, "게스트 방향", null, today());
        Long oldOccurrence = id(oldOccurrences(token, today(), today()), "$.data[0].id");
        practice(token, oldOccurrence, ActionVariant.MINIMUM, today());
        String migrated = migrate(token, system);
        Long goal = id(migrated, "$.data.goalId");
        mockMvc.perform(post("/api/v1/goals/{id}/direct-logs", goal).header("Authorization", "Bearer " + token)
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, "guest-log").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practicedDate\":\"" + today() + "\",\"title\":\"직접 기록\"}"))
                .andExpect(status().isCreated());
        User target = userRepository.save(new User("migration-merge-target@example.com", passwordEncoder.encode("password123"), "병합 대상"));
        String login = mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"migration-merge-target@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String targetToken = JsonPath.read(login, "$.data.accessToken");
        assertEquals(target.getId(), goalRepository.findById(goal).orElseThrow().getOwner().getId());
        assertEquals(target.getId(), systemRepository.findById(system).orElseThrow().getOwner().getId());
        mockMvc.perform(get("/api/v1/goals/{id}/journey", goal).header("Authorization", "Bearer " + targetToken)
                        .param("from", today().toString()).param("to", today().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.practiceOccurrenceCount").value(1))
                .andExpect(jsonPath("$.data.summary.directLogCount").value(1));
        assertEquals(goal, id(migrate(targetToken, system), "$.data.goalId"));
        mockMvc.perform(get("/api/v1/goals/{id}", goal).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료 정리는 실제 mapping FK가 있는 원본·새 모델을 자식부터 지우고 다른 계정 데이터는 유지한다")
    void expiredGuestCleanupWithForeignKeys() throws Exception {
        User guest = userRepository.save(User.guest(LocalDateTime.now(Constant.ZONE).plusDays(31)));
        String token = tokens.createGuestAccessToken(guest).tokenValue();
        Long system = createSystem(token, "만료 방향", null, today());
        String old = oldOccurrences(token, today(), today());
        Long task = id(old, "$.data[0].linkedTaskId");
        practice(token, id(old, "$.data[0].id"), ActionVariant.STANDARD, today());
        String migrated = migrate(token, system);
        Long goal = id(migrated, "$.data.goalId");
        Long plan = id(migrated, "$.data.planId");
        String nativeTask = mockMvc.perform(post("/api/v1/goals/{id}/tasks", goal).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"일회성\",\"date\":\"" + today() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long nativeTaskId = id(nativeTask, "$.data.id");
        mockMvc.perform(post("/api/v1/goals/{id}/direct-logs", goal).header("Authorization", "Bearer " + token)
                        .header(CorsConfig.IDEMPOTENCY_KEY_HEADER, "cleanup-direct").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"practicedDate\":\"" + today() + "\",\"title\":\"직접 기록\"}"))
                .andExpect(status().isCreated());
        String controlToken = token("migration-cleanup-control@example.com");
        Long controlSystem = createSystem(controlToken, "유지 방향", null, today());
        guest.refreshGuestExpiration(LocalDateTime.now(Constant.ZONE).minusHours(1));
        userRepository.save(guest);
        cleanupService.deleteExpiredGuests(LocalDateTime.now(Constant.ZONE));
        assertFalse(userRepository.existsById(guest.getId()));
        assertFalse(systemRepository.existsById(system));
        assertFalse(goalRepository.existsById(goal));
        assertFalse(planRepository.existsById(plan));
        assertFalse(taskRepository.existsById(task));
        assertFalse(taskRepository.existsById(nativeTaskId));
        assertTrue(systemRepository.existsById(controlSystem));
    }

    private String migrate(String token, Long system) throws Exception {
        return mockMvc.perform(post("/api/v1/personal-systems/{id}/goal-migration", system).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Long createSystem(String token, String direction, Long dday, LocalDate start) throws Exception {
        var request = new PersonalSystemCreateRequest(direction, "이유", "아침 식사 뒤", schedule(start),
                new PersonalSystemActionRequest("기본 10개", "10개", 10), new PersonalSystemActionRequest("최소 1개", "1개", 5),
                "책 열기", dday);
        String response = mockMvc.perform(post("/api/v1/personal-systems").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return id(response, "$.data.id");
    }

    private PersonalSystemScheduleRequest schedule(LocalDate start) {
        return new PersonalSystemScheduleRequest(start, List.of(ScheduleDay.values()), "Asia/Seoul", java.time.LocalTime.of(8, 0));
    }

    private String oldOccurrences(String token, LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/v1/personal-system-occurrences").header("Authorization", "Bearer " + token)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private PersonalSystemPracticeResultRequest practiceRequest(ActionVariant variant, LocalDate date) {
        return new PersonalSystemPracticeResultRequest(variant, date.atTime(9, 0), "Asia/Seoul", "기존 실천 메모",
                List.of(PracticeDifficulty.NO_TIME), NextActionPatchKind.REPLACE, "문제집 열기", null);
    }

    private void practice(String token, Long occurrence, ActionVariant variant, LocalDate date) throws Exception {
        mockMvc.perform(put("/api/v1/personal-system-occurrences/{id}/result", occurrence).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(practiceRequest(variant, date))))
                .andExpect(status().isOk());
    }

    private LocalDate today() { return LocalDate.now(Constant.ZONE); }
    private Long id(String response, String path) { return ((Number) JsonPath.read(response, path)).longValue(); }
    private String token(String email) {
        return tokens.createAccessToken(userRepository.save(new User(email, "encoded", "이관 사용자"))).tokenValue();
    }
}
