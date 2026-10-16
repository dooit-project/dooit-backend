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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.goal.ai.GoalDraftProperties;
import pj.dooit.goal.ai.GoalJourneySummaryProvider;
import pj.dooit.goal.domain.GoalDirectLog;
import pj.dooit.goal.dto.GoalCreateRequest;
import pj.dooit.goal.dto.GoalDirectLogRequest;
import pj.dooit.goal.dto.GoalJourneySummaryRequest;
import pj.dooit.goal.repository.GoalDirectLogRepository;
import pj.dooit.goal.repository.GoalPlanOccurrenceRepository;
import pj.dooit.goal.repository.GoalRepository;
import pj.dooit.goal.service.GoalDirectLogService;
import pj.dooit.goal.service.GoalJourneyService;
import pj.dooit.goal.service.GoalService;
import pj.dooit.mail.MailService;
import pj.dooit.task.repository.TaskRepository;
import pj.dooit.user.domain.User;
import pj.dooit.user.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.batch.job.enabled=false", "app.api-logging.payload-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class GoalJourneySummaryV1IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired GoalService goals;
    @Autowired GoalRepository goalRepository;
    @Autowired GoalDirectLogRepository logs;
    @Autowired GoalDirectLogService logService;
    @Autowired GoalJourneyService journeys;
    @Autowired TaskRepository tasks;
    @Autowired GoalPlanOccurrenceRepository occurrences;
    @Autowired GoalDraftProperties properties;
    @MockitoBean MailService mailService;
    @MockitoBean GoalJourneySummaryProvider provider;
    private User owner;
    private String token;
    private long goal;
    private final LocalDate day = LocalDate.of(2026, 10, 16);

    @BeforeEach void prepare() {
        assertThat(properties.isEnabled()).isFalse();
        owner = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "요약 사용자"));
        token = tokens.createAccessToken(owner).tokenValue();
        goal = goals.create(new GoalCreateRequest("private-goal", null, null, null, null), owner).id();
    }
    @AfterEach void resetProperties() { properties.setEnabled(false); }

    @Test void completeAggregateWithExplicitlyTruncatedEvidenceAndExactSourceHighlights(CapturedOutput output) throws Exception {
        addLogs(101);
        var snapshot = journeys.get(goal, day, day, null, null, 100, owner);
        properties.setEnabled(true);
        when(provider.select(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var context = (GoalJourneySummaryProvider.Context) call.getArgument(0);
            assertThat(context.summary().directLogCount()).isEqualTo(101);
            assertThat(context.evidence()).hasSize(100);
            assertThat(context.evidenceTruncated()).isTrue();
            assertThat(context.evidence().getFirst().index()).isZero();
            assertThat(context.evidence().getFirst().note()).isEqualTo("private-note-100");
            assertThat(context.evidence().getLast().index()).isEqualTo(99);
            return new GoalJourneySummaryProvider.Selection(List.of(0, 99));
        });
        long beforeLogs = logs.count(), beforeTasks = tasks.count(), beforeOccurrences = occurrences.count();
        summary(goal, new GoalJourneySummaryRequest(day, day, null, snapshot.journeyVersion()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.data.journeyVersion").value(snapshot.journeyVersion()))
                .andExpect(jsonPath("$.data.summary.directLogCount").value(101))
                .andExpect(jsonPath("$.data.summary.practiceDateCount").value(1))
                .andExpect(jsonPath("$.data.overview").value("기록된 실천 101건 · 실천 날짜 1일 · 휴식 회차 0건 · 기록 없는 회차 0건"))
                .andExpect(jsonPath("$.data.evidenceCount").value(100))
                .andExpect(jsonPath("$.data.evidenceTruncated").value(true))
                .andExpect(jsonPath("$.data.highlights.length()").value(2))
                .andExpect(jsonPath("$.data.highlights[0].sourceId").value(snapshot.items().getFirst().sourceId()))
                .andExpect(jsonPath("$.data.highlights[1].note").value(snapshot.items().getLast().note()));
        assertThat(logs.count()).isEqualTo(beforeLogs);
        assertThat(tasks.count()).isEqualTo(beforeTasks);
        assertThat(occurrences.count()).isEqualTo(beforeOccurrences);
        assertThat(output).doesNotContain("private-", token);
    }

    @Test void changesOutsideEvidenceWindowInvalidateExpectedVersionBeforeProviderCall() throws Exception {
        var ids = addLogs(101);
        var old = journeys.get(goal, day, day, null, null, 100, owner);
        logService.update(ids.getFirst(), new GoalDirectLogRequest(day, "Asia/Seoul", "private-updated", "private-changed-note"), owner);
        properties.setEnabled(true);
        summary(goal, new GoalJourneySummaryRequest(day, day, null, old.journeyVersion()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value(70008));
        verifyNoInteractions(provider);
    }

    @Test void disabledAndOwnershipAndAuthenticationDoNotCallProvider() throws Exception {
        addLogs(1);
        var request = new GoalJourneySummaryRequest(day, day, null, null);
        summary(goal, request).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value(70009));
        var other = users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "다른 사용자"));
        long otherGoal = goals.create(new GoalCreateRequest("other", null, null, null, null), other).id();
        summary(otherGoal, request).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value(70001));
        summary(Long.MAX_VALUE, request).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/goals/{id}/journey-summary", goal).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
    }

    @Test void enabledEmptyJourneyNeedsNoProviderAndDoesNotInferAchievements() throws Exception {
        properties.setEnabled(true);
        summary(goal, new GoalJourneySummaryRequest(day, day, null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.overview").value("기록된 실천 0건 · 실천 날짜 0일 · 휴식 회차 0건 · 기록 없는 회차 0건"))
                .andExpect(jsonPath("$.data.highlights.length()").value(0))
                .andExpect(jsonPath("$.data.evidenceCount").value(0))
                .andExpect(jsonPath("$.data.evidenceTruncated").value(false));
        verifyNoInteractions(provider);
    }

    @Test void invalidSelectionsAndProviderErrorsCannotInventSummaryContent(CapturedOutput output) throws Exception {
        addLogs(1);
        properties.setEnabled(true);
        for (var bad : new GoalJourneySummaryProvider.Selection[] {null,
                new GoalJourneySummaryProvider.Selection(null), new GoalJourneySummaryProvider.Selection(List.of()),
                new GoalJourneySummaryProvider.Selection(Arrays.asList((Integer) null)),
                new GoalJourneySummaryProvider.Selection(List.of(-1)), new GoalJourneySummaryProvider.Selection(List.of(1)),
                new GoalJourneySummaryProvider.Selection(List.of(0, 0)),
                new GoalJourneySummaryProvider.Selection(List.of(0, 0, 0, 0, 0, 0))}) {
            when(provider.select(any())).thenReturn(bad);
            summary(goal, new GoalJourneySummaryRequest(day, day, null, null)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value(70009));
        }
        when(provider.select(any())).thenThrow(new IllegalStateException("private-provider-prompt-and-credential"));
        summary(goal, new GoalJourneySummaryRequest(day, day, null, null)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/goals/{id}/journey", goal).header("Authorization", "Bearer " + token)
                .param("from", day.toString()).param("to", day.toString())).andExpect(status().isOk());
        assertThat(output).doesNotContain("private-");
    }

    @Test void validatesRangeZoneAndVersionWith366DayBoundary() throws Exception {
        properties.setEnabled(true);
        for (var invalid : new GoalJourneySummaryRequest[] {
                new GoalJourneySummaryRequest(null, day, null, null),
                new GoalJourneySummaryRequest(day, null, null, null),
                new GoalJourneySummaryRequest(day, day.minusDays(1), null, null),
                new GoalJourneySummaryRequest(day, day.plusDays(366), null, null),
                new GoalJourneySummaryRequest(day, day, "private-invalid-zone", null),
                new GoalJourneySummaryRequest(day, day, null, "bad-version"),
                new GoalJourneySummaryRequest(LocalDate.MAX, LocalDate.MAX, null, null)}) {
            summary(goal, invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value(10001));
        }
        summary(goal, new GoalJourneySummaryRequest(day, day.plusDays(365), null, null)).andExpect(status().isOk());
        verifyNoInteractions(provider);
    }

    private List<Long> addLogs(int count) {
        var entity = goalRepository.findById(goal).orElseThrow();
        var records = new ArrayList<GoalDirectLog>();
        for (int i = 0; i < count; i++) records.add(new GoalDirectLog(owner, entity, 1, day, "Asia/Seoul",
                "private-title-" + i, "private-note-" + i, UUID.randomUUID().toString(), null));
        return logs.saveAll(records).stream().map(GoalDirectLog::getId).toList();
    }

    private ResultActions summary(long id, GoalJourneySummaryRequest request) throws Exception {
        return mvc.perform(post("/api/v1/goals/{id}/journey-summary", id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)));
    }
}
