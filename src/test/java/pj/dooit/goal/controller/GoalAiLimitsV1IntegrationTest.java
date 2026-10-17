package pj.dooit.goal.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.goal.ai.*;
import pj.dooit.goal.domain.ScheduleDay;
import pj.dooit.goal.dto.*;
import pj.dooit.goal.service.GoalService;
import pj.dooit.goal.service.GoalPlanService;
import pj.dooit.goal.service.GoalDirectLogService;
import pj.dooit.mail.MailService;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.batch.job.enabled=false", "app.goal-ai.max-requests-per-window=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalAiLimitsV1IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired GoalService goals;
    @Autowired GoalPlanService plans;
    @Autowired GoalDirectLogService logs;
    @Autowired GoalDraftProperties properties;
    @MockitoBean MailService mailService;
    @MockitoBean GoalDraftProvider provider;
    @MockitoBean GoalPlanDraftProvider planProvider;
    @MockitoBean GoalAdjustmentDraftProvider adjustmentProvider;
    @MockitoBean GoalJourneySummaryProvider summaryProvider;
    @AfterEach void reset() { properties.setEnabled(false); }

    @Test void fourApisShareAccountQuotaAndReturnRetryAfterWithoutAffectingCrudOrOtherAccounts() throws Exception {
        User owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "사용자"));
        String token = tokens.createAccessToken(owner).tokenValue();
        var day = LocalDate.of(2026, 10, 17);
        long goal = goals.create(new GoalCreateRequest("목표", null, null, null, null), owner).id();
        long plan = plans.create(new GoalPlanCreateRequest(goal, "계획", null,
                new GoalPlanScheduleRequest(day, List.of(ScheduleDay.MO), null, null),
                new GoalPlanActionRequest("기본", null, null), new GoalPlanActionRequest("최소", null, null), null), owner).id();
        logs.create(goal, UUID.randomUUID().toString(), new GoalDirectLogRequest(day, null, "기록", null), owner);
        mvc.perform(post("/api/v1/goals/draft").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"설명\"}"))
                .andExpect(status().isServiceUnavailable());
        properties.setEnabled(true);
        when(provider.draft(anyString())).thenReturn(new GoalDraftProvider.Suggestion("초안", null, null));
        mvc.perform(post("/api/v1/goals/draft").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"설명\"}"))
                .andExpect(status().isOk());
        String[][] attempts = {
                {"/api/v1/goals/draft", "{\"description\":\"설명\"}"},
                {"/api/v1/goals/" + goal + "/plan-draft", "{\"description\":\"설명\",\"schedule\":{\"startDate\":\"2026-10-17\",\"days\":[\"MO\"]}}"},
                {"/api/v1/goals/" + goal + "/adjustment-draft", "{\"planId\":" + plan + ",\"description\":\"설명\"}"},
                {"/api/v1/goals/" + goal + "/journey-summary", "{\"from\":\"2026-10-17\",\"to\":\"2026-10-17\"}"}};
        for (String[] attempt : attempts) {
            mvc.perform(post(attempt[0]).header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(attempt[1]))
                    .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.error.code").value(70010))
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        }
        verifyNoInteractions(planProvider, adjustmentProvider, summaryProvider);
        User other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        mvc.perform(post("/api/v1/goals/draft").header("Authorization", "Bearer " + tokens.createAccessToken(other).tokenValue())
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"설명\"}"))
                .andExpect(status().isOk());
        verify(provider, times(2)).draft(anyString());
        mvc.perform(get("/api/v1/goals/{id}", goal).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }
}
