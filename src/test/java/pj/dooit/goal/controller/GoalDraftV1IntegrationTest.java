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
    @MockitoBean MailService mailService;
    @MockitoBean GoalDraftProvider provider;
    String token;

    @BeforeEach void prepare() {
        assertThat(properties.isEnabled()).isFalse();
        token = tokens.createAccessToken(users.save(new User(UUID.randomUUID() + "@example.com", "encoded", "초안 사용자"))).tokenValue();
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
}
