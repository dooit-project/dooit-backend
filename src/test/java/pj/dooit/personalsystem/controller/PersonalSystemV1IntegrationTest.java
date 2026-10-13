package pj.dooit.personalsystem.controller;

import com.jayway.jsonpath.JsonPath;
import pj.dooit.auth.service.JwtTokenService;
import pj.dooit.mail.MailService;
import pj.dooit.personalsystem.domain.ActionVariant;
import pj.dooit.personalsystem.domain.NextActionPatchKind;
import pj.dooit.personalsystem.domain.ScheduleDay;
import pj.dooit.personalsystem.dto.PersonalSystemActionRequest;
import pj.dooit.personalsystem.dto.PersonalSystemCreateRequest;
import pj.dooit.personalsystem.dto.PersonalSystemPracticeResultRequest;
import pj.dooit.personalsystem.dto.PersonalSystemScheduleRequest;
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
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PersonalSystemV1IntegrationTest {

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
    @DisplayName("개인 시스템 생성 후 기간 조회는 예정 회차를 중복 없이 생성한다")
    void createAndListOccurrences_success() throws Exception {
        String accessToken = accessToken("personal-system-occurrence@example.com");
        Long systemId = createSystem(accessToken);

        mockMvc.perform(get("/api/v1/personal-system-occurrences")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-10-12")
                        .param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].systemId").value(systemId))
                .andExpect(jsonPath("$.data[0].serviceDate").value("2026-10-12"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].notificationKey").value("personal-system:%d:2026-10-12".formatted(systemId)))
                .andExpect(jsonPath("$.data[0].scheduledNotificationAt").value("2026-10-12T20:00:00"))
                .andExpect(jsonPath("$.data[1].serviceDate").value("2026-10-19"));

        mockMvc.perform(get("/api/v1/personal-system-occurrences")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-10-12")
                        .param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("회차 실천 결과와 휴식은 증거 집계에 반영되고 취소할 수 있다")
    void resultRestAndEvidence_success() throws Exception {
        String accessToken = accessToken("personal-system-evidence@example.com");
        Long systemId = createSystem(accessToken);
        Long occurrenceId = firstOccurrence(accessToken);

        PersonalSystemPracticeResultRequest resultRequest = new PersonalSystemPracticeResultRequest(
                ActionVariant.MINIMUM,
                LocalDateTime.of(2026, 10, 12, 21, 0),
                "Asia/Seoul",
                "최소 행동 완료",
                List.of(),
                NextActionPatchKind.KEEP,
                null,
                null
        );
        mockMvc.perform(put("/api/v1/personal-system-occurrences/{occurrenceId}/result", occurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resultRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionVariant").value("MINIMUM"))
                .andExpect(jsonPath("$.data.accomplishedNote").value("최소 행동 완료"));

        mockMvc.perform(get("/api/v1/personal-systems/{systemId}/evidence", systemId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-10-12")
                        .param("to", "2026-10-19")
                        .param("timeZone", "Asia/Seoul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practicedOccurrenceCount").value(1))
                .andExpect(jsonPath("$.data.practicedDateCount").value(1))
                .andExpect(jsonPath("$.data.practicedCountsByVariant.MINIMUM").value(1))
                .andExpect(jsonPath("$.data.restedCount").value(0))
                .andExpect(jsonPath("$.data.noRecordCount").value(1));

        mockMvc.perform(delete("/api/v1/personal-system-occurrences/{occurrenceId}/result", occurrenceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(put("/api/v1/personal-system-occurrences/{occurrenceId}/rest", occurrenceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESTED"));

        mockMvc.perform(get("/api/v1/personal-systems/{systemId}/evidence", systemId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-10-12")
                        .param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practicedOccurrenceCount").value(0))
                .andExpect(jsonPath("$.data.restedCount").value(1))
                .andExpect(jsonPath("$.data.noRecordCount").value(1));
    }

    @Test
    @DisplayName("오늘 이후 회차는 Task를 연결하고 Task 완료/재개가 회차 결과와 동기화된다")
    void linkedTaskCompleteAndReopen_syncsOccurrence() throws Exception {
        String accessToken = accessToken("personal-system-linked-task@example.com");
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        Long systemId = createSystem(accessToken, nextMonday, List.of(ScheduleDay.MO));

        String response = mockMvc.perform(get("/api/v1/personal-system-occurrences")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", nextMonday.toString())
                        .param("to", nextMonday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].linkedTaskId").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number occurrenceId = JsonPath.read(response, "$.data[0].id");
        Number taskId = JsonPath.read(response, "$.data[0].linkedTaskId");

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", taskId.longValue())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", nextMonday.atTime(21, 0).toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/personal-systems/{systemId}/evidence", systemId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", nextMonday.toString())
                        .param("to", nextMonday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practicedOccurrenceCount").value(1))
                .andExpect(jsonPath("$.data.practicedCountsByVariant.UNKNOWN").value(1))
                .andExpect(jsonPath("$.data.occurrences[0].id").value(occurrenceId.longValue()))
                .andExpect(jsonPath("$.data.occurrences[0].status").value("PRACTICED"));

        mockMvc.perform(patch("/api/v1/tasks/{id}/done/cancel", taskId.longValue())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", nextMonday.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/personal-systems/{systemId}/evidence", systemId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", nextMonday.toString())
                        .param("to", nextMonday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.practicedOccurrenceCount").value(0))
                .andExpect(jsonPath("$.data.noRecordCount").value(1))
                .andExpect(jsonPath("$.data.occurrences[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("다른 사용자의 개인 시스템은 조회할 수 없다")
    void ownerIsolation_notFound() throws Exception {
        String ownerToken = accessToken("personal-system-owner@example.com");
        String otherToken = accessToken("personal-system-other@example.com");
        Long systemId = createSystem(ownerToken);

        mockMvc.perform(get("/api/v1/personal-systems/{systemId}", systemId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(60001));
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "System 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createSystem(String accessToken) throws Exception {
        return createSystem(accessToken, LocalDate.of(2026, 10, 12), List.of(ScheduleDay.MO));
    }

    private Long createSystem(String accessToken, LocalDate startDate, List<ScheduleDay> days) throws Exception {
        PersonalSystemCreateRequest request = new PersonalSystemCreateRequest(
                "건강한 저녁 루틴 만들기",
                "수면의 질을 높이기 위해",
                "저녁 식사 후",
                new PersonalSystemScheduleRequest(
                        startDate,
                        days,
                        "Asia/Seoul",
                        LocalTime.of(20, 0)
                ),
                new PersonalSystemActionRequest("30분 걷기", "30분", 30),
                new PersonalSystemActionRequest("운동화 신고 밖에 나가기", "1분", 1),
                "운동화 꺼내기",
                null
        );

        String response = mockMvc.perform(post("/api/v1/personal-systems")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long firstOccurrence(String accessToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/personal-system-occurrences")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-10-12")
                        .param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data[0].id");
        return id.longValue();
    }
}
