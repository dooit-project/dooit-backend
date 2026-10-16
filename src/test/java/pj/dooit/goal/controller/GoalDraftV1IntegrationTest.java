package pj.dooit.goal.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalDraftProvider;
import pj.dooit.goal.ai.GoalPlanDraftProvider;
import pj.dooit.goal.ai.GoalAdjustmentDraftProvider;
import pj.dooit.goal.dto.GoalPlanCreateRequest;
import pj.dooit.goal.dto.GoalPlanScheduleRequest;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.service.GoalPlanService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalPlanActionRequest;
import pj.dooit.goal.service.GoalService;
import pj.dooit.goal.repository.GoalPlanRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalPlanVersionRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.mail.MailService;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.batch.job.enabled=false", "app.api-logging.payload-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class GoalDraftV1IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired TaskRepository tasks;
    @Autowired GoalDraftProperties properties;
    @Autowired GoalService goalService;
    @Autowired GoalPlanService planService;
    @Autowired GoalPlanRepository plans;
    @Autowired GoalPlanVersionRepository planVersions;
    @Autowired GoalPlanOccurrenceRepository occurrences;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MailService mailService;
    @MockitoBean GoalDraftProvider provider;
    @MockitoBean GoalPlanDraftProvider planProvider;
    @MockitoBean GoalAdjustmentDraftProvider adjustmentProvider;
    String token;
    User owner;

    @BeforeEach void prepare() {
        assertThat(properties.isEnabled()).isFalse();
        owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "초안 사용자"));
        token = tokens.createAccessToken(owner).tokenValue();
    }
    @AfterEach void resetProperties() { properties.setEnabled(false); }

    @Test void disabledDoesNotCallProviderAndCrudStillWorks() throws Exception {
        draft("{\"description\":\"private-description\"}")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        verifyNoInteractions(provider);
        mvc.perform(post("/api/v1/goals").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"직접 작성\"}"))
                .andExpect(status().isCreated());
    }

    @Test void enabledReturnsUnsavedDraftWithUserDateAndNoPrivateLogs(CapturedOutput output) throws Exception {
        properties.setEnabled(true);
        when(provider.draft("private-description")).thenReturn(new GoalDraftProvider.Suggestion(
                " private-title ", " private-outcome ", " private-motivation "));
        long beforeGoals = goals.count(), beforeTasks = tasks.count();
        draft("{\"description\":\"  private-description  \",\"targetDate\":\"2026-11-30\"}")
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.title").value("private-title"))
                .andExpect(jsonPath("$.data.desiredOutcome").value("private-outcome"))
                .andExpect(jsonPath("$.data.motivationText").value("private-motivation"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-11-30"))
                .andExpect(jsonPath("$.data.id").doesNotExist());
        assertThat(goals.count()).isEqualTo(beforeGoals);
        assertThat(tasks.count()).isEqualTo(beforeTasks);
        assertThat(output).doesNotContain("private-");
        assertThat(output).doesNotContain(token);
        verify(provider).draft("private-description");
    }

    @Test void providerFailureDoesNotLeakMessagesOrBreakCrud(CapturedOutput output) throws Exception {
        properties.setEnabled(true);
        when(provider.draft(anyString())).thenThrow(new IllegalStateException("private-provider-token-and-prompt"));
        draft("{\"description\":\"private-description\"}")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-"))));
        assertThat(output).doesNotContain("private-");
        mvc.perform(get("/api/v1/goals").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test void invalidProviderOutputsAreUnavailable() throws Exception {
        properties.setEnabled(true);
        for (GoalDraftProvider.Suggestion bad : new GoalDraftProvider.Suggestion[] {
                null, new GoalDraftProvider.Suggestion(" ", null, null),
                new GoalDraftProvider.Suggestion("x".repeat(121), null, null),
                new GoalDraftProvider.Suggestion("title", "x".repeat(501), null),
                new GoalDraftProvider.Suggestion("title", null, "x".repeat(501))}) {
            when(provider.draft(anyString())).thenReturn(bad);
            draft("{\"description\":\"private-description\"}")
                    .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009));
        }
    }

    @Test void validationAndAuthenticationRunBeforeProvider(CapturedOutput output) throws Exception {
        properties.setEnabled(true);
        for (String invalid : new String[] {"{}", "{\"description\":\" \"}",
                "{\"description\":\"" + "x".repeat(2001) + "\"}",
                "{\"description\":\"ok\",\"targetDate\":\"private-invalid-date\"}", "{broken"}) {
            draft(invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(10001));
        }
        mvc.perform(post("/api/v1/goals/draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"private-description\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
        assertThat(output).doesNotContain("private-invalid-date");
    }

    private ResultActions draft(String body) throws Exception {
        return mvc.perform(post("/api/v1/goals/draft").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void planDraftUsesOwnedSnapshotAndDoesNotPersistUntilConfirmed(CapturedOutput output) throws Exception {
        long goalId = goalService.create(new GoalCreateRequest("private-goal-title", "private-outcome",
                "private-motivation", null, null), owner).id();
        properties.setEnabled(true);
        when(planProvider.draft(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var context = (GoalPlanDraftProvider.Context) call.getArgument(0);
            assertThat(context.goalTitle()).isEqualTo("private-goal-title");
            assertThat(context.desiredOutcome()).isEqualTo("private-outcome");
            assertThat(context.motivationText()).isEqualTo("private-motivation");
            assertThat(context.description()).isEqualTo("private-plan-description");
            return validPlanSuggestion();
        });
        long beforePlans = plans.count(), beforeVersions = planVersions.count(), beforeTasks = tasks.count(),
                beforeOccurrences = occurrences.count();
        String body = planDraft(goalId, validPlanBody()).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.goalId").value(goalId))
                .andExpect(jsonPath("$.data.goalVersion").value(1))
                .andExpect(jsonPath("$.data.name").value("private-plan-name"))
                .andExpect(jsonPath("$.data.standardAction.text").value("private-standard"))
                .andExpect(jsonPath("$.data.minimumAction.text").value("private-minimum"))
                .andExpect(jsonPath("$.data.schedule.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.data.schedule.days.length()").value(2))
                .andExpect(jsonPath("$.data.schedule.days[0]").value("MO"))
                .andExpect(jsonPath("$.data.schedule.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.schedule.notifyAtTime").value("09:30:00"))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(plans.count()).isEqualTo(beforePlans);
        assertThat(planVersions.count()).isEqualTo(beforeVersions);
        assertThat(tasks.count()).isEqualTo(beforeTasks);
        assertThat(occurrences.count()).isEqualTo(beforeOccurrences);
        assertThat(output).doesNotContain("private-");
        Map<String, Object> confirmed = JsonPath.read(body, "$.data");
        confirmed.remove("goalVersion");
        mvc.perform(post("/api/v1/goals/{id}/plans", goalId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(confirmed)))
                .andExpect(status().isCreated());
        assertThat(plans.count()).isEqualTo(beforePlans + 1);
        assertThat(planVersions.count()).isEqualTo(beforeVersions + 1);
    }

    @Test void planOwnershipIsCheckedBeforeAvailabilityOrProviderCall() throws Exception {
        User other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        long otherGoal = goalService.create(new GoalCreateRequest("다른 목표", null, null, null, null), other).id();
        long ownGoal = goalService.create(new GoalCreateRequest("내 목표", null, null, null, null), owner).id();
        planDraft(otherGoal, validPlanBody()).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        planDraft(Long.MAX_VALUE, validPlanBody()).andExpect(status().isNotFound());
        planDraft(ownGoal, validPlanBody()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009));
        verifyNoInteractions(planProvider);
        properties.setEnabled(true);
        planDraft(otherGoal, validPlanBody()).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/goals/{id}/plan-draft", ownGoal).contentType(MediaType.APPLICATION_JSON)
                .content(validPlanBody())).andExpect(status().isUnauthorized());
        verifyNoInteractions(planProvider);
    }

    @Test void invalidPlanInputsNeverCallProvider() throws Exception {
        long id = goalService.create(new GoalCreateRequest("내 목표", null, null, null, null), owner).id();
        properties.setEnabled(true);
        for (String invalid : new String[] {"{}", "{\"description\":\"hi\"}",
                validPlanBody().replace("private-plan-description", " "),
                validPlanBody().replace("private-plan-description", "x".repeat(2001)),
                validPlanBody().replace("\"MO\",\"WE\",\"MO\"", ""),
                validPlanBody().replace("\"MO\",\"WE\",\"MO\"", "null"),
                validPlanBody().replace("\"MO\",\"WE\",\"MO\"", "\"BAD\""),
                validPlanBody().replace("Asia/Seoul", "private-invalid-zone"),
                validPlanBody().replace("2026-11-01", "bad-date")}) {
            planDraft(id, invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(10001));
        }
        verifyNoInteractions(planProvider);
    }

    @Test void planDraftDefaultsOnlyMissingZoneAndNormalizesOptionalText() throws Exception {
        long id = goalService.create(new GoalCreateRequest("내 목표", null, null, null, null), owner).id();
        properties.setEnabled(true);
        when(planProvider.draft(any())).thenReturn(new GoalPlanDraftProvider.Suggestion("계획", " ",
                new GoalPlanActionRequest(" 기본 행동 ", " ", null), action("최소 행동", null), " "));
        for (String zone : new String[] {"null", "\" \""}) {
            planDraft(id, validPlanBody().replace("\" Asia/Seoul \"", zone))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.schedule.timeZone").value("Asia/Seoul"))
                    .andExpect(jsonPath("$.data.triggerText").isEmpty())
                    .andExpect(jsonPath("$.data.standardAction.targetAmount").isEmpty())
                    .andExpect(jsonPath("$.data.standardAction.estimatedMinutes").isEmpty())
                    .andExpect(jsonPath("$.data.nextAction").isEmpty());
        }
    }

    @Test void badPlanProviderOutputsAndFailuresAreIsolated(CapturedOutput output) throws Exception {
        long id = goalService.create(new GoalCreateRequest("내 목표", null, null, null, null), owner).id();
        properties.setEnabled(true);
        for (var bad : new GoalPlanDraftProvider.Suggestion[] {null,
                new GoalPlanDraftProvider.Suggestion(" ", null, action("ok", 1), action("ok", 1), null),
                new GoalPlanDraftProvider.Suggestion("x".repeat(121), null, action("ok", 1), action("ok", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, null, action("ok", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, action(" ", 1), action("ok", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, action("ok", 0), action("ok", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, action("ok", 1), action("ok", 1441), null)}) {
            when(planProvider.draft(any())).thenReturn(bad);
            planDraft(id, validPlanBody()).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value(70009));
        }
        when(planProvider.draft(any())).thenThrow(new IllegalStateException("private-provider-error"));
        planDraft(id, validPlanBody()).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/goals/{id}", id).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertThat(output).doesNotContain("private-");
    }

    private GoalPlanActionRequest action(String text, Integer minutes) {
        return new GoalPlanActionRequest(text, null, minutes);
    }

    private GoalPlanDraftProvider.Suggestion validPlanSuggestion() {
        return new GoalPlanDraftProvider.Suggestion(" private-plan-name ", " private-trigger ",
                action(" private-standard ", 20), action(" private-minimum ", 2), " private-next ");
    }

    private String validPlanBody() {
        return """
                {"description":"  private-plan-description  ","schedule":{"startDate":"2026-11-01",
                 "days":["MO","WE","MO"],"timeZone":" Asia/Seoul ","notifyAtTime":"09:30:00"}}
                """;
    }

    private ResultActions planDraft(long id, String body) throws Exception {
        return mvc.perform(post("/api/v1/goals/{id}/plan-draft", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test void adjustmentUsesSnapshotOutsideTransactionAndOnlyExplicitPatchPersists(CapturedOutput output) throws Exception {
        long goal = goalService.create(new GoalCreateRequest("private-adjustment-goal", "private-outcome", null, null, null), owner).id();
        long plan = createPlan(goal, owner);
        properties.setEnabled(true);
        when(adjustmentProvider.draft(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var context = (GoalAdjustmentDraftProvider.Context) call.getArgument(0);
            assertThat(context.goalTitle()).isEqualTo("private-adjustment-goal");
            assertThat(context.planName()).isEqualTo("private-current-name");
            assertThat(context.standardAction().text()).isEqualTo("private-current-standard");
            assertThat(context.minimumAction().text()).isEqualTo("private-current-minimum");
            assertThat(context.description()).isEqualTo("private-adjustment-description");
            return validPlanSuggestion();
        });
        var before = planService.get(plan, owner);
        long beforePlans = plans.count(), beforeVersions = planVersions.count(), beforeTasks = tasks.count(), beforeOccurrences = occurrences.count();
        String body = adjustment(goal, plan, "  private-adjustment-description  ")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.goalId").value(goal)).andExpect(jsonPath("$.data.goalVersion").value(1))
                .andExpect(jsonPath("$.data.planId").value(plan)).andExpect(jsonPath("$.data.planVersion").value(1))
                .andExpect(jsonPath("$.data.name").value("private-plan-name"))
                .andExpect(jsonPath("$.data.standardAction.text").value("private-standard"))
                .andExpect(jsonPath("$.data.minimumAction.text").value("private-minimum"))
                .andExpect(jsonPath("$.data.schedule.startDate").value("2026-11-01"))
                .andExpect(jsonPath("$.data.schedule.days[0]").value("MO"))
                .andExpect(jsonPath("$.data.schedule.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.schedule.notifyAtTime").value("09:30:00"))
                .andReturn().getResponse().getContentAsString();
        assertThat(planService.get(plan, owner)).isEqualTo(before);
        assertThat(plans.count()).isEqualTo(beforePlans);
        assertThat(planVersions.count()).isEqualTo(beforeVersions);
        assertThat(tasks.count()).isEqualTo(beforeTasks);
        assertThat(occurrences.count()).isEqualTo(beforeOccurrences);
        assertThat(output).doesNotContain("private-");
        Map<String, Object> confirmed = JsonPath.read(body, "$.data");
        confirmed.remove("goalVersion"); confirmed.remove("planId"); confirmed.remove("planVersion");
        mvc.perform(patch("/api/v1/goal-plans/{id}", plan).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(confirmed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentVersion").value(2));
        assertThat(plans.count()).isEqualTo(beforePlans);
        assertThat(planVersions.count()).isEqualTo(beforeVersions + 1);
    }

    @Test void adjustmentChecksGoalPlanOwnershipAndLinkBeforeAvailability() throws Exception {
        long goal = goalService.create(new GoalCreateRequest("목표", null, null, null, null), owner).id();
        long secondGoal = goalService.create(new GoalCreateRequest("다른 내 목표", null, null, null, null), owner).id();
        long plan = createPlan(goal, owner), wrongGoalPlan = createPlan(secondGoal, owner), independent = createPlan(null, owner);
        User other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        long otherGoal = goalService.create(new GoalCreateRequest("남의 목표", null, null, null, null), other).id();
        long otherPlan = createPlan(otherGoal, other);
        for (long invalid : new long[] {wrongGoalPlan, independent, otherPlan, Long.MAX_VALUE}) {
            adjustment(goal, invalid, "설명").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70002));
        }
        adjustment(otherGoal, otherPlan, "설명").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        adjustment(Long.MAX_VALUE, plan, "설명").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        adjustment(goal, plan, "설명").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009));
        properties.setEnabled(true);
        adjustment(goal, wrongGoalPlan, "설명").andExpect(status().isNotFound());
        verifyNoInteractions(adjustmentProvider);
    }

    @Test void invalidAdjustmentRequestsDoNotCallProvider() throws Exception {
        properties.setEnabled(true);
        for (String body : new String[] {"{}", "{\"planId\":0,\"description\":\"text\"}",
                "{\"planId\":-1,\"description\":\"text\"}", "{\"planId\":1,\"description\":\" \"}",
                "{\"planId\":1,\"description\":\"" + "x".repeat(2001) + "\"}"}) {
            mvc.perform(post("/api/v1/goals/1/adjustment-draft").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(10001));
        }
        mvc.perform(post("/api/v1/goals/1/adjustment-draft").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":1,\"description\":\"text\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adjustmentProvider);
    }

    @Test void adjustmentFailureDoesNotLeakOrChangePlan(CapturedOutput output) throws Exception {
        long goal = goalService.create(new GoalCreateRequest("목표", null, null, null, null), owner).id();
        long plan = createPlan(goal, owner);
        var before = planService.get(plan, owner);
        properties.setEnabled(true);
        for (var bad : new GoalPlanDraftProvider.Suggestion[] {null,
                new GoalPlanDraftProvider.Suggestion(" ", null, action("text", 1), action("text", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, action("text", 0), action("text", 1), null),
                new GoalPlanDraftProvider.Suggestion("name", null, action("text", 1), null, null)}) {
            when(adjustmentProvider.draft(any())).thenReturn(bad);
            adjustment(goal, plan, "private-adjustment-description").andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value(70009));
        }
        when(adjustmentProvider.draft(any())).thenThrow(new IllegalStateException("private-provider-credential"));
        adjustment(goal, plan, "private-adjustment-description").andExpect(status().isServiceUnavailable());
        assertThat(planService.get(plan, owner)).isEqualTo(before);
        assertThat(output).doesNotContain("private-");
    }

    private long createPlan(Long goal, User user) {
        return planService.create(new GoalPlanCreateRequest(goal, "private-current-name", "private-current-trigger",
                new GoalPlanScheduleRequest(LocalDate.of(2026, 11, 1), List.of(ScheduleDay.MO), "Asia/Seoul", LocalTime.of(9, 30)),
                action("private-current-standard", 20), action("private-current-minimum", 2), "private-current-next"), user).id();
    }

    private ResultActions adjustment(long goal, long plan, String description) throws Exception {
        return mvc.perform(post("/api/v1/goals/{id}/adjustment-draft", goal).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                        Map.of("planId", plan, "description", description))));
    }
}
