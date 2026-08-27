package com.todolab.calendar.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.mail.MailService;
import com.todolab.task.domain.TaskType;
import com.todolab.task.dto.TaskRequest;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarFeedV1IntegrationTest {

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
    @DisplayName("v1 calendar feed token을 발급하고 인증 없이 ICS를 조회한다")
    void calendarFeed_issueAndReadIcs() throws Exception {
        String accessToken = accessToken("calendar-feed@example.com");
        createTask(accessToken, new TaskRequest(
                "외부 캘린더 일정",
                "설명은 feed에 노출하지 않는다",
                TaskType.SCHEDULE,
                LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0),
                LocalDateTime.now().plusDays(3).withHour(11).withMinute(0).withSecond(0).withNano(0),
                null,
                false
        ));

        String response = mockMvc.perform(post("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.feedPath").value(containsString("/api/v1/calendar-feeds/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.data.token");

        String ics = mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"todolab.ics\""))
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("SUMMARY:외부 캘린더 일정")
                .contains("UID:task-")
                .doesNotContain("설명은 feed에 노출하지 않는다");
    }

    @Test
    @DisplayName("calendar feed token 재발급은 기존 token을 폐기한다")
    void calendarFeed_reissueRevokesPreviousToken() throws Exception {
        String accessToken = accessToken("calendar-feed-reissue@example.com");
        String firstToken = issueToken(accessToken);
        String secondToken = issueToken(accessToken);

        assertThat(firstToken).isNotEqualTo(secondToken);

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", firstToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", secondToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("calendar feed token 폐기 후 feed 조회는 404를 반환한다")
    void calendarFeed_revoke() throws Exception {
        String accessToken = accessToken("calendar-feed-revoke@example.com");
        String token = issueToken(accessToken);

        mockMvc.perform(delete("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/calendar-feeds/{token}.ics", token))
                .andExpect(status().isNotFound());
    }

    private String issueToken(String accessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/calendar-feed/token")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private void createTask(String accessToken, TaskRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Calendar 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }
}
