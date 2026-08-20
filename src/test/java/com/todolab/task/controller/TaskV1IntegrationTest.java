package com.todolab.task.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.dday.dto.DdayGoalRequest;
import com.todolab.mail.MailService;
import com.todolab.task.domain.RecurrenceExceptionType;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.RecurrenceSeries;
import com.todolab.task.domain.Task;
import com.todolab.task.domain.TaskStatus;
import com.todolab.task.domain.TaskType;
import com.todolab.task.dto.TaskQuickCaptureRequest;
import com.todolab.task.dto.TaskRequest;
import com.todolab.task.dto.TaskRecurrenceRequest;
import com.todolab.task.dto.TodayOrderRequest;
import com.todolab.task.repository.RecurrenceSeriesRepository;
import com.todolab.task.repository.TaskRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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
class TaskV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    RecurrenceSeriesRepository recurrenceSeriesRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 Task 생성 응답은 날짜 없는 Task 기본값과 nullable 필드를 반환한다")
    void create_unscheduledTask_responseDefaults() throws Exception {
        String accessToken = accessToken("task-inbox@example.com");
        TaskRequest request = new TaskRequest("인박스 정리", null, null, null, null, false);

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.type").value("TODO"))
                .andExpect(jsonPath("$.data.title").value("인박스 정리"))
                .andExpect(jsonPath("$.data.description").isEmpty())
                .andExpect(jsonPath("$.data.startAt").isEmpty())
                .andExpect(jsonPath("$.data.endAt").isEmpty())
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.unscheduled").value(true))
                .andExpect(jsonPath("$.data.category").isEmpty())
                .andExpect(jsonPath("$.data.status").value("INBOX"))
                .andExpect(jsonPath("$.data.plannedDate").isEmpty())
                .andExpect(jsonPath("$.data.targetDate").isEmpty())
                .andExpect(jsonPath("$.data.todayOrder").isEmpty())
                .andExpect(jsonPath("$.data.completedAt").isEmpty())
                .andExpect(jsonPath("$.data.carryOverCount").value(0))
                .andExpect(jsonPath("$.data.staleCarryOver").value(false))
                .andExpect(jsonPath("$.data.deferReason").isEmpty())
                .andExpect(jsonPath("$.data.deferReasonLabel").isEmpty())
                .andExpect(jsonPath("$.data.ddayGoalId").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt").isEmpty());
    }

    @Test
    @DisplayName("v1 Task 생성 응답은 날짜 있는 Task를 Today로 저장하고 날짜 규칙을 반환한다")
    void create_scheduledTask_responseDateRules() throws Exception {
        String accessToken = accessToken("task-schedule@example.com");
        TaskRequest request = new TaskRequest(
                "출시 회의",
                "릴리스 범위 확인",
                LocalDateTime.of(2026, 7, 22, 9, 0),
                LocalDateTime.of(2026, 7, 22, 10, 0),
                "업무",
                false
        );

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.description").value("릴리스 범위 확인"))
                .andExpect(jsonPath("$.data.startAt").value("2026-07-22T09:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-07-22T10:00:00"))
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.unscheduled").value(false))
                .andExpect(jsonPath("$.data.category").value("업무"))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.plannedDate").value("2026-07-22"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-07-22"))
                .andExpect(jsonPath("$.data.todayOrder").isEmpty())
                .andExpect(jsonPath("$.data.completedAt").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.updatedAt").isEmpty());
    }

    @Test
    @DisplayName("v1 Task 빠른 등록은 상대 날짜와 시간을 파싱해 일정을 생성한다")
    void quickCapture_relativeDateAndTime_success() throws Exception {
        String accessToken = accessToken("task-quick-capture-date@example.com");
        TaskQuickCaptureRequest request = new TaskQuickCaptureRequest(
                "내일 3시 출시 회의",
                LocalDate.of(2026, 8, 13),
                "Asia/Seoul",
                "업무"
        );

        mockMvc.perform(post("/api/v1/tasks/quick-capture")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.parsed").value(true))
                .andExpect(jsonPath("$.data.originalText").value("내일 3시 출시 회의"))
                .andExpect(jsonPath("$.data.parsedDate").value("2026-08-14"))
                .andExpect(jsonPath("$.data.parsedTime").value("15:00:00"))
                .andExpect(jsonPath("$.data.parsedType").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.task.title").value("출시 회의"))
                .andExpect(jsonPath("$.data.task.type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.task.startAt").value("2026-08-14T15:00:00"))
                .andExpect(jsonPath("$.data.task.endAt").value("2026-08-14T16:00:00"))
                .andExpect(jsonPath("$.data.task.allDay").value(false))
                .andExpect(jsonPath("$.data.task.category").value("업무"))
                .andExpect(jsonPath("$.data.task.status").value("TODAY"));
    }

    @Test
    @DisplayName("v1 Task 빠른 등록은 단독 요일 표현을 가장 가까운 해당 요일 날짜로 파싱한다")
    void quickCapture_singleWeekday_success() throws Exception {
        String accessToken = accessToken("task-quick-capture-weekday@example.com");
        TaskQuickCaptureRequest request = new TaskQuickCaptureRequest(
                "금요일 병원",
                LocalDate.of(2026, 8, 13),
                "Asia/Seoul",
                "건강"
        );

        mockMvc.perform(post("/api/v1/tasks/quick-capture")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parsed").value(true))
                .andExpect(jsonPath("$.data.parsedDate").value("2026-08-14"))
                .andExpect(jsonPath("$.data.parsedTime").isEmpty())
                .andExpect(jsonPath("$.data.parsedRecurrenceFrequency").isEmpty())
                .andExpect(jsonPath("$.data.task.title").value("병원"))
                .andExpect(jsonPath("$.data.task.type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.task.startAt").value("2026-08-14T00:00:00"))
                .andExpect(jsonPath("$.data.task.endAt").value("2026-08-15T00:00:00"))
                .andExpect(jsonPath("$.data.task.allDay").value(true))
                .andExpect(jsonPath("$.data.task.category").value("건강"));
    }

    @Test
    @DisplayName("v1 Task 빠른 등록은 파싱하지 못한 원문을 Inbox TODO로 저장한다")
    void quickCapture_plainTextFallback_success() throws Exception {
        String accessToken = accessToken("task-quick-capture-fallback@example.com");
        TaskQuickCaptureRequest request = new TaskQuickCaptureRequest(
                "그냥 메모",
                LocalDate.of(2026, 8, 13),
                null,
                null
        );

        mockMvc.perform(post("/api/v1/tasks/quick-capture")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parsed").value(false))
                .andExpect(jsonPath("$.data.originalText").value("그냥 메모"))
                .andExpect(jsonPath("$.data.parsedDate").isEmpty())
                .andExpect(jsonPath("$.data.parsedTime").isEmpty())
                .andExpect(jsonPath("$.data.parsedType").value("TODO"))
                .andExpect(jsonPath("$.data.task.title").value("그냥 메모"))
                .andExpect(jsonPath("$.data.task.type").value("TODO"))
                .andExpect(jsonPath("$.data.task.status").value("INBOX"))
                .andExpect(jsonPath("$.data.task.unscheduled").value(true));
    }

    @Test
    @DisplayName("v1 Task 빠른 등록은 매주 요일 입력을 반복 일정으로 저장한다")
    void quickCapture_weeklyRecurrence_success() throws Exception {
        String accessToken = accessToken("task-quick-capture-weekly@example.com");
        TaskQuickCaptureRequest request = new TaskQuickCaptureRequest(
                "매주 월요일 오전 9시 운동",
                LocalDate.of(2026, 8, 13),
                "Asia/Seoul",
                null
        );

        mockMvc.perform(post("/api/v1/tasks/quick-capture")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parsed").value(true))
                .andExpect(jsonPath("$.data.parsedDate").value("2026-08-17"))
                .andExpect(jsonPath("$.data.parsedTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.parsedRecurrenceFrequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.parsedByDays[0]").value("MO"))
                .andExpect(jsonPath("$.data.task.title").value("운동"))
                .andExpect(jsonPath("$.data.task.startAt").value("2026-08-17T09:00:00"))
                .andExpect(jsonPath("$.data.task.endAt").value("2026-08-17T10:00:00"))
                .andExpect(jsonPath("$.data.task.recurrenceSeriesId").value(notNullValue()))
                .andExpect(jsonPath("$.data.task.recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.task.recurrence.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO"));
    }

    @Test
    @DisplayName("v1 Task 생성은 매주 화요일 9시 반복 일정을 저장하고 조회 시 occurrence를 생성한다")
    void create_recurringWeeklySchedule_success() throws Exception {
        String accessToken = accessToken("task-recurrence-create@example.com");
        TaskRequest request = new TaskRequest(
                "주간 회의",
                "화요일 싱크",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 7, 9, 0),
                LocalDateTime.of(2026, 7, 7, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("TU"),
                        null
                )
        );

        String response = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("주간 회의"))
                .andExpect(jsonPath("$.data.startAt").value("2026-07-07T09:00:00"))
                .andExpect(jsonPath("$.data.recurrenceSeriesId").value(notNullValue()))
                .andExpect(jsonPath("$.data.occurrenceDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data.originalOccurrenceDate").value("2026-07-07"))
                .andExpect(jsonPath("$.data.recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.recurrence.interval").value(1))
                .andExpect(jsonPath("$.data.recurrence.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=1;BYDAY=TU;COUNT=3"))
                .andExpect(jsonPath("$.data.recurrence.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.recurrence.recurrenceStartAt").value("2026-07-07T09:00:00"))
                .andExpect(jsonPath("$.data.recurrence.recurrenceCount").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number recurrenceSeriesId = JsonPath.read(response, "$.data.recurrenceSeriesId");

        RecurrenceSeries series = recurrenceSeriesRepository.findById(recurrenceSeriesId.longValue()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(series.getFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        org.assertj.core.api.Assertions.assertThat(series.getInterval()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(series.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;INTERVAL=1;BYDAY=TU;COUNT=3");
        org.assertj.core.api.Assertions.assertThat(series.getTimeZone()).isEqualTo("Asia/Seoul");
        org.assertj.core.api.Assertions.assertThat(series.getRecurrenceCount()).isEqualTo(3);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-07T09:00:00"))
                .andExpect(jsonPath("$.data[1].startAt").value("2026-07-14T09:00:00"))
                .andExpect(jsonPath("$.data[1].recurrenceSeriesId").value(recurrenceSeriesId.longValue()))
                .andExpect(jsonPath("$.data[1].recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data[1].occurrenceDate").value("2026-07-14"))
                .andExpect(jsonPath("$.data[2].startAt").value("2026-07-21T09:00:00"));
    }

    @Test
    @DisplayName("v1 반복 Task 생성은 시작일이 반복 규칙과 맞지 않으면 거부한다")
    void create_recurringSchedule_fail_startDateMismatch() throws Exception {
        String accessToken = accessToken("task-recurrence-start-mismatch@example.com");
        TaskRequest request = new TaskRequest(
                "잘못된 반복 회의",
                "화요일 반복인데 수요일 시작",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 8, 9, 0),
                LocalDateTime.of(2026, 7, 8, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("TU"),
                        null
                )
        );

        mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    @Test
    @DisplayName("v1 알림 후보 조회는 반복 occurrence를 생성하고 완료/삭제 항목을 제외한다")
    void getNotificationCandidates_materializesRecurrenceAndExcludesDoneOrSkipped() throws Exception {
        String accessToken = accessToken("task-notification-candidates@example.com");
        Long normalTaskId = createTask(accessToken, new TaskRequest(
                "단건 알림",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 28, 14, 0),
                LocalDateTime.of(2026, 7, 28, 15, 0),
                "업무",
                false
        ));
        createTask(accessToken, new TaskRequest("인박스 제외", null, TaskType.TODO, null, null, null, false));

        TaskRequest recurrenceRequest = new TaskRequest(
                "반복 알림",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 28, 9, 0),
                LocalDateTime.of(2026, 7, 28, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        2,
                        List.of("TU"),
                        null
                )
        );
        String recurrenceResponse = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recurrenceRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number recurrenceSeriesId = JsonPath.read(recurrenceResponse, "$.data.recurrenceSeriesId");

        mockMvc.perform(get("/api/v1/tasks/notification-candidates")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-07-28")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].notificationKey").value("recurrence:%d:2026-07-28".formatted(recurrenceSeriesId.longValue())))
                .andExpect(jsonPath("$.data[0].scheduledAt").value("2026-07-28T09:00:00"))
                .andExpect(jsonPath("$.data[0].task.title").value("반복 알림"))
                .andExpect(jsonPath("$.data[1].notificationKey").value("task:%d".formatted(normalTaskId)))
                .andExpect(jsonPath("$.data[1].scheduledAt").value("2026-07-28T14:00:00"))
                .andExpect(jsonPath("$.data[2].notificationKey").value("recurrence:%d:2026-08-04".formatted(recurrenceSeriesId.longValue())))
                .andExpect(jsonPath("$.data[2].scheduledAt").value("2026-08-04T09:00:00"));

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", normalTaskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-07-28T18:00:00"))
                .andExpect(status().isOk());
        Long secondOccurrenceId = taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(
                        recurrenceSeriesId.longValue(),
                        userRepository.findByEmail("task-notification-candidates@example.com").orElseThrow().getId()
                )
                .get(1)
                .getId();
        mockMvc.perform(delete("/api/v1/tasks/{id}", secondOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/notification-candidates")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-07-28")
                        .param("to", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].notificationKey").value("recurrence:%d:2026-07-28".formatted(recurrenceSeriesId.longValue())));
    }

    @Test
    @DisplayName("v1 MONTH Task 조회는 YYYY-MM을 바인딩하고 owner 범위 월간 일정을 반환한다")
    void getTasks_month_success_yearMonthBindingAndOwnerScope() throws Exception {
        String ownerToken = accessToken("task-month-owner@example.com");
        String otherOwnerToken = accessToken("task-month-other@example.com");

        createTask(ownerToken, new TaskRequest(
                "7월 일정",
                null,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 10, 0),
                null,
                false
        ));
        createTask(ownerToken, new TaskRequest(
                "월말 걸침",
                null,
                LocalDateTime.of(2026, 7, 31, 23, 0),
                LocalDateTime.of(2026, 8, 1, 1, 0),
                null,
                false
        ));
        createTask(ownerToken, new TaskRequest(
                "8월 일정",
                null,
                LocalDateTime.of(2026, 8, 1, 9, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null,
                false
        ));
        createTask(otherOwnerToken, new TaskRequest(
                "다른 사용자 7월 일정",
                null,
                LocalDateTime.of(2026, 7, 2, 9, 0),
                LocalDateTime.of(2026, 7, 2, 10, 0),
                null,
                false
        ));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("7월 일정"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-01T09:00:00"))
                .andExpect(jsonPath("$.data[1].title").value("월말 걸침"))
                .andExpect(jsonPath("$.data[1].startAt").value("2026-07-31T23:00:00"));
    }

    @Test
    @DisplayName("v1 MONTH Task 조회는 YYYY-MM-DD 형식을 거부한다")
    void getTasks_month_fail_invalidDateFormat() throws Exception {
        String accessToken = accessToken("task-month-invalid@example.com");

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07-22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    @DisplayName("v1 Task 통합 검색은 필터, 날짜 출처, cursor, owner scope를 적용한다")
    void searchTasks_success_filtersCursorAndOwnerScope() throws Exception {
        String ownerToken = accessToken("task-search-owner@example.com");
        String otherOwnerToken = accessToken("task-search-other@example.com");

        Long firstTaskId = createTask(ownerToken, new TaskRequest(
                "출시 회의",
                "Release 범위 확인",
                LocalDateTime.of(2026, 7, 22, 9, 0),
                LocalDateTime.of(2026, 7, 22, 10, 0),
                "업무",
                false
        ));
        Long secondTaskId = createTask(ownerToken, new TaskRequest(
                "출시 리허설",
                "영문 release 점검",
                LocalDateTime.of(2026, 7, 23, 9, 0),
                LocalDateTime.of(2026, 7, 23, 10, 0),
                "업무",
                false
        ));
        createTask(ownerToken, new TaskRequest("출시 아이디어", null, null, null, "업무", false));
        createTask(otherOwnerToken, new TaskRequest(
                "출시 회의",
                null,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                LocalDateTime.of(2026, 7, 22, 10, 0),
                "업무",
                false
        ));

        String firstPage = mockMvc.perform(get("/api/v1/tasks/search")
                                .header("Authorization", "Bearer " + ownerToken)
                                .param("q", "출시")
                                .param("statuses", "TODAY")
                                .param("taskTypes", "SCHEDULE")
                                .param("category", "업무")
                                .param("allDay", "false")
                                .param("dateField", "PLANNED")
                                .param("dateFrom", "2026-07-01")
                                .param("dateTo", "2026-07-31")
                                .param("sort", "RELEVANT_DATE_ASC")
                                .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].task.id").value(firstTaskId))
                .andExpect(jsonPath("$.data.items[0].task.title").value("출시 회의"))
                .andExpect(jsonPath("$.data.items[0].task.description").value("Release 범위 확인"))
                .andExpect(jsonPath("$.data.items[0].task.status").value("TODAY"))
                .andExpect(jsonPath("$.data.items[0].task.type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.items[0].relevantDate").value("2026-07-22"))
                .andExpect(jsonPath("$.data.items[0].dateSource").value("TARGET_DATE"))
                .andExpect(jsonPath("$.data.items[0].matchedFields[0]").value("TITLE"))
                .andExpect(jsonPath("$.data.items[0].highlight").value("출시 회의"))
                .andExpect(jsonPath("$.data.nextCursor").value(String.valueOf(firstTaskId)))
                .andExpect(jsonPath("$.data.limit").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextCursor = JsonPath.read(firstPage, "$.data.nextCursor");

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("q", "RELEASE")
                        .param("statuses", "TODAY")
                        .param("taskTypes", "SCHEDULE")
                        .param("dateField", "PLANNED")
                        .param("dateFrom", "2026-07-01")
                        .param("dateTo", "2026-07-31")
                        .param("cursor", nextCursor)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].task.id").value(secondTaskId))
                .andExpect(jsonPath("$.data.items[0].task.title").value("출시 리허설"))
                .andExpect(jsonPath("$.data.items[0].relevantDate").value("2026-07-23"))
                .andExpect(jsonPath("$.data.items[0].matchedFields[0]").value("DESCRIPTION"))
                .andExpect(jsonPath("$.data.items[0].highlight").value("영문 release 점검"))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty());
    }

    @Test
    @DisplayName("v1 Task 통합 검색은 category와 D-Day 제목 매칭 정보를 반환한다")
    void searchTasks_success_categoryAndDdayMatches() throws Exception {
        String accessToken = accessToken("task-search-rich-match@example.com");
        Long categoryTaskId = createTask(accessToken, new TaskRequest(
                "자료 정리",
                null,
                LocalDateTime.of(2026, 7, 24, 9, 0),
                LocalDateTime.of(2026, 7, 24, 10, 0),
                "출시",
                false
        ));
        Long ddayTaskId = createTask(accessToken, new TaskRequest(
                "체크리스트",
                null,
                LocalDateTime.of(2026, 7, 25, 9, 0),
                LocalDateTime.of(2026, 7, 25, 10, 0),
                "업무",
                false
        ));
        Long ddayGoalId = createDdayGoal(accessToken, "출시 목표", "2026-08-01");

        mockMvc.perform(patch("/api/v1/tasks/{id}/dday-goal", ddayTaskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("ddayGoalId", String.valueOf(ddayGoalId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", "출시")
                        .param("sort", "RELEVANT_DATE_ASC")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].task.id").value(categoryTaskId))
                .andExpect(jsonPath("$.data.items[0].matchedFields[0]").value("CATEGORY"))
                .andExpect(jsonPath("$.data.items[0].highlight").value("출시"))
                .andExpect(jsonPath("$.data.items[1].task.id").value(ddayTaskId))
                .andExpect(jsonPath("$.data.items[1].matchedFields[0]").value("DDAY_GOAL_TITLE"))
                .andExpect(jsonPath("$.data.items[1].highlight").value("출시 목표"));
    }

    @Test
    @DisplayName("v1 Task 통합 검색 cursor는 offset 대신 마지막 Task id 기준으로 다음 페이지를 이어간다")
    void searchTasks_cursorUsesLastTaskIdAnchor() throws Exception {
        String accessToken = accessToken("task-search-cursor-anchor@example.com");
        Long firstTaskId = createTask(accessToken, new TaskRequest(
                "커서 둘째",
                null,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                LocalDateTime.of(2026, 7, 22, 10, 0),
                "업무",
                false
        ));
        Long secondTaskId = createTask(accessToken, new TaskRequest(
                "커서 셋째",
                null,
                LocalDateTime.of(2026, 7, 23, 9, 0),
                LocalDateTime.of(2026, 7, 23, 10, 0),
                "업무",
                false
        ));

        String firstPage = mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", "커서")
                        .param("sort", "RELEVANT_DATE_ASC")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].task.id").value(firstTaskId))
                .andExpect(jsonPath("$.data.nextCursor").value(String.valueOf(firstTaskId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextCursor = JsonPath.read(firstPage, "$.data.nextCursor");

        createTask(accessToken, new TaskRequest(
                "커서 첫째",
                null,
                LocalDateTime.of(2026, 7, 21, 9, 0),
                LocalDateTime.of(2026, 7, 21, 10, 0),
                "업무",
                false
        ));

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("q", "커서")
                        .param("sort", "RELEVANT_DATE_ASC")
                        .param("cursor", nextCursor)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].task.id").value(secondTaskId))
                .andExpect(jsonPath("$.data.items[0].task.title").value("커서 셋째"));
    }

    @Test
    @DisplayName("v1 Task 통합 검색은 잘못된 enum, 날짜 범위, cursor를 거부한다")
    void searchTasks_fail_invalidParameters() throws Exception {
        String accessToken = accessToken("task-search-invalid@example.com");

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("statuses", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("dateFrom", "2026-07-31")
                        .param("dateTo", "2026-07-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));

        mockMvc.perform(get("/api/v1/tasks/search")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("cursor", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));
    }

    @Test
    @DisplayName("v1 Today 일괄 재정렬은 전체 실행 순서를 저장하고 Today 조회 순서와 일치한다")
    void reorderToday_success_bulkOrder() throws Exception {
        String accessToken = accessToken("task-bulk-order@example.com");

        Long firstId = moveToToday(accessToken, createTask(accessToken, new TaskRequest("첫 번째", null, null, null, null, false)), "2026-07-22");
        Long secondId = moveToToday(accessToken, createTask(accessToken, new TaskRequest("두 번째", null, null, null, null, false)), "2026-07-22");
        Long thirdId = moveToToday(accessToken, createTask(accessToken, new TaskRequest("세 번째", null, null, null, null, false)), "2026-07-22");
        TodayOrderRequest request = new TodayOrderRequest(java.time.LocalDate.parse("2026-07-22"), List.of(thirdId, firstId, secondId));

        mockMvc.perform(put("/api/v1/tasks/today-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(thirdId))
                .andExpect(jsonPath("$.data[0].todayOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(firstId))
                .andExpect(jsonPath("$.data[1].todayOrder").value(1))
                .andExpect(jsonPath("$.data[2].id").value(secondId))
                .andExpect(jsonPath("$.data[2].todayOrder").value(2));

        mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(thirdId))
                .andExpect(jsonPath("$.data[1].id").value(firstId))
                .andExpect(jsonPath("$.data[2].id").value(secondId));
    }

    @Test
    @DisplayName("v1 Today/Calendar overlap은 여러 날 일정을 포함하지만 Today 일괄 재정렬 대상에서는 제외한다")
    void todayAndCalendarOverlap_excludesScheduleFromBulkReorder() throws Exception {
        String accessToken = accessToken("task-overlap-reorder@example.com");

        Long firstId = moveToToday(
                accessToken,
                createTask(accessToken, new TaskRequest("첫 번째 실행", null, null, null, null, false)),
                "2026-07-22"
        );
        Long secondId = moveToToday(
                accessToken,
                createTask(accessToken, new TaskRequest("두 번째 실행", null, null, null, null, false)),
                "2026-07-22"
        );
        Long scheduleId = createTask(accessToken, new TaskRequest(
                "여러 날 일정",
                null,
                LocalDateTime.of(2026, 7, 21, 23, 0),
                LocalDateTime.of(2026, 7, 23, 1, 0),
                null,
                false
        ));

        mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[1].id").value(secondId))
                .andExpect(jsonPath("$.data[2].id").value(scheduleId))
                .andExpect(jsonPath("$.data[2].type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data[2].todayOrder").isEmpty());

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "DAY")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(scheduleId))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-21T23:00:00"))
                .andExpect(jsonPath("$.data[0].endAt").value("2026-07-23T01:00:00"));

        mockMvc.perform(put("/api/v1/tasks/today-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TodayOrderRequest(
                                java.time.LocalDate.parse("2026-07-22"),
                                List.of(secondId, firstId)
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[1].id").value(firstId));

        mockMvc.perform(put("/api/v1/tasks/today-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TodayOrderRequest(
                                java.time.LocalDate.parse("2026-07-22"),
                                List.of(secondId, firstId, scheduleId)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value(20002));
    }

    @Test
    @DisplayName("v1 Today 일괄 재정렬은 중복 ID를 400, stale 목록을 409로 거부한다")
    void reorderToday_fail_duplicateAndConflict() throws Exception {
        String accessToken = accessToken("task-bulk-order-invalid@example.com");

        Long firstId = moveToToday(accessToken, createTask(accessToken, new TaskRequest("첫 번째", null, null, null, null, false)), "2026-07-22");
        Long secondId = moveToToday(accessToken, createTask(accessToken, new TaskRequest("두 번째", null, null, null, null, false)), "2026-07-22");
        Long scheduleId = createTask(accessToken, new TaskRequest(
                "일정",
                null,
                LocalDateTime.of(2026, 7, 22, 9, 0),
                LocalDateTime.of(2026, 7, 22, 10, 0),
                null,
                false
        ));

        mockMvc.perform(put("/api/v1/tasks/today-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TodayOrderRequest(
                                java.time.LocalDate.parse("2026-07-22"),
                                List.of(firstId, firstId)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"));

        mockMvc.perform(put("/api/v1/tasks/today-order")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TodayOrderRequest(
                                java.time.LocalDate.parse("2026-07-22"),
                                List.of(secondId, scheduleId)
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(20002));
    }

    @Test
    @DisplayName("v1 Today/Calendar 조회는 반복 series occurrence를 materialize해서 반환한다")
    void recurrenceOccurrenceMaterialize_todayAndCalendar() throws Exception {
        String accessToken = accessToken("task-recurrence-materialize@example.com");
        User owner = userRepository.findByEmail("task-recurrence-materialize@example.com").orElseThrow();
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=3",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                null,
                3
        ));
        Task template = Task.builder()
                .title("반복 회의")
                .description("주간 싱크")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 7, 6, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 6, 10, 0))
                .category("업무")
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .originalOccurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .build();
        taskRepository.save(template);

        String todayResponse = mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("반복 회의"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-13T09:00:00"))
                .andExpect(jsonPath("$.data[0].targetDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data[0].recurrenceSeriesId").value(series.getId()))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data[0].originalOccurrenceDate").value("2026-07-13"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number occurrenceId = JsonPath.read(todayResponse, "$.data[0].id");

        mockMvc.perform(patch("/api/v1/tasks/{id}/defer-reason", occurrenceId.longValue())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("reason", "WAITING_OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deferReason").value("WAITING_OTHER"))
                .andExpect(jsonPath("$.data.recurrenceSeriesId").value(series.getId()))
                .andExpect(jsonPath("$.data.occurrenceDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data.recurrence.frequency").value("WEEKLY"));

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", occurrenceId.longValue())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-07-13T18:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-07-13T18:00:00"))
                .andExpect(jsonPath("$.data.recurrenceSeriesId").value(series.getId()))
                .andExpect(jsonPath("$.data.occurrenceDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data.recurrence.frequency").value("WEEKLY"));

        mockMvc.perform(get("/api/v1/tasks/done")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(occurrenceId.longValue()))
                .andExpect(jsonPath("$.data[0].recurrenceSeriesId").value(series.getId()))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data[0].recurrence.frequency").value("WEEKLY"));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-06T09:00:00"))
                .andExpect(jsonPath("$.data[1].startAt").value("2026-07-13T09:00:00"))
                .andExpect(jsonPath("$.data[1].status").value("DONE"))
                .andExpect(jsonPath("$.data[1].occurrenceDate").value("2026-07-13"))
                .andExpect(jsonPath("$.data[2].startAt").value("2026-07-20T09:00:00"))
                .andExpect(jsonPath("$.data[2].status").value("TODAY"))
                .andExpect(jsonPath("$.data[2].occurrenceDate").value("2026-07-20"));
    }

    @Test
    @DisplayName("v1 반복 Task는 THIS_AND_FUTURE scope로 현재와 미래 occurrence를 수정/삭제한다")
    void recurrenceScope_updateAndDeleteThisAndFuture() throws Exception {
        String accessToken = accessToken("task-recurrence-scope@example.com");
        User owner = userRepository.findByEmail("task-recurrence-scope@example.com").orElseThrow();
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=8",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                null,
                8
        ));
        taskRepository.save(Task.builder()
                .title("반복 회의")
                .description("주간 싱크")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 7, 6, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 6, 10, 0))
                .category("업무")
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .originalOccurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .build());

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        Long secondOccurrenceId = taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(series.getId(), owner.getId())
                .get(1)
                .getId();
        TaskRequest updateRequest = new TaskRequest(
                "변경 회의",
                "범위 수정",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 13, 11, 0),
                LocalDateTime.of(2026, 7, 13, 12, 0),
                "변경",
                false
        );

        mockMvc.perform(put("/api/v1/tasks/{id}", secondOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("recurrenceScope", "THIS_AND_FUTURE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("변경 회의"))
                .andExpect(jsonPath("$.data.startAt").value("2026-07-13T11:00:00"));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("반복 회의"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-06T09:00:00"))
                .andExpect(jsonPath("$.data[1].title").value("변경 회의"))
                .andExpect(jsonPath("$.data[1].startAt").value("2026-07-13T11:00:00"))
                .andExpect(jsonPath("$.data[2].title").value("변경 회의"))
                .andExpect(jsonPath("$.data[2].startAt").value("2026-07-20T11:00:00"))
                .andExpect(jsonPath("$.data[3].title").value("변경 회의"))
                .andExpect(jsonPath("$.data[3].startAt").value("2026-07-27T11:00:00"));

        mockMvc.perform(delete("/api/v1/tasks/{id}", secondOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("recurrenceScope", "THIS_AND_FUTURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-07-06"));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("v1 Task 수정은 반복 규칙 자체 변경을 거부한다")
    void recurrenceRuleUpdate_fail_notSupported() throws Exception {
        String accessToken = accessToken("task-recurrence-rule-update@example.com");
        Long taskId = createTask(accessToken, new TaskRequest(
                "규칙 수정 대상",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 7, 9, 0),
                LocalDateTime.of(2026, 7, 7, 10, 0),
                null,
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("TU"),
                        null
                )
        ));
        TaskRequest updateRequest = new TaskRequest(
                "규칙 수정 요청",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 7, 11, 0),
                LocalDateTime.of(2026, 7, 7, 12, 0),
                null,
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("WE"),
                        null
                )
        );

        mockMvc.perform(put("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(10001));
    }

    @Test
    @DisplayName("v1 Today 조회는 반복 생성 직후 첫 occurrence의 반복 상세를 반환한다")
    void getTodayTasks_success_recurringOccurrenceAfterCreate() throws Exception {
        String accessToken = accessToken("task-recurrence-today-after-create@example.com");
        Long taskId = createTask(accessToken, new TaskRequest(
                "반복 Today 조회",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("TU"),
                        null
                )
        ));

        mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(taskId))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-04"))
                .andExpect(jsonPath("$.data[0].originalOccurrenceDate").value("2026-08-04"))
                .andExpect(jsonPath("$.data[0].recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data[0].recurrence.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=1;BYDAY=TU;COUNT=3"));
    }

    @Test
    @DisplayName("v1 반복 occurrence 건너뛰기는 DELETE THIS로 SKIPPED marker를 남기고 조회와 알림 후보에서 제외한다")
    void deleteRecurringOccurrence_thisScope_marksSkippedAndExcludesFromQueries() throws Exception {
        String accessToken = accessToken("task-recurrence-skip-this@example.com");
        User owner = userRepository.findByEmail("task-recurrence-skip-this@example.com").orElseThrow();
        Long firstTaskId = createTask(accessToken, new TaskRequest(
                "반복 건너뛰기",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 8, 4, 9, 0),
                LocalDateTime.of(2026, 8, 4, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        3,
                        List.of("TU"),
                        null
                )
        ));
        Long recurrenceSeriesId = taskRepository.findById(firstTaskId).orElseThrow().getRecurrenceSeries().getId();

        String secondTodayResponse = mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-08-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-11"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number secondOccurrenceId = JsonPath.read(secondTodayResponse, "$.data[0].id");

        mockMvc.perform(delete("/api/v1/tasks/{id}", secondOccurrenceId.longValue())
                        .header("Authorization", "Bearer " + accessToken)
                        .param("recurrenceScope", "THIS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        Task skipped = taskRepository.findById(secondOccurrenceId.longValue()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(skipped.getRecurrenceException()).isEqualTo(RecurrenceExceptionType.SKIPPED);
        org.assertj.core.api.Assertions.assertThat(skipped.getOccurrenceDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        org.assertj.core.api.Assertions.assertThat(skipped.getOwner().getId()).isEqualTo(owner.getId());

        mockMvc.perform(get("/api/v1/tasks/today")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-08-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-04"))
                .andExpect(jsonPath("$.data[1].occurrenceDate").value("2026-08-18"));

        mockMvc.perform(get("/api/v1/tasks/notification-candidates")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("from", "2026-08-04")
                        .param("to", "2026-08-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-04"))
                .andExpect(jsonPath("$.data[1].occurrenceDate").value("2026-08-18"));

        org.assertj.core.api.Assertions.assertThat(
                taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(recurrenceSeriesId, owner.getId())
        ).hasSize(3);
    }

    @Test
    @DisplayName("v1 반복 Rule 변경은 선택 occurrence 이후 일반 미래 occurrence를 새 rule 기준으로 정리한다")
    void recurrenceRuleUpdate_success_recreatesFuturePlainOccurrences() throws Exception {
        String accessToken = accessToken("task-recurrence-rule-update-success@example.com");
        User owner = userRepository.findByEmail("task-recurrence-rule-update-success@example.com").orElseThrow();
        Long firstTaskId = createTask(accessToken, new TaskRequest(
                "규칙 변경 회의",
                null,
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 7, 9, 0),
                LocalDateTime.of(2026, 7, 7, 10, 0),
                "업무",
                false,
                new TaskRecurrenceRequest(
                        RecurrenceFrequency.WEEKLY,
                        1,
                        null,
                        null,
                        null,
                        4,
                        List.of("TU"),
                        null
                )
        ));
        Long recurrenceSeriesId = taskRepository.findById(firstTaskId).orElseThrow().getRecurrenceSeries().getId();

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));

        List<Task> before = taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(
                recurrenceSeriesId,
                owner.getId()
        );
        Long secondOccurrenceId = before.get(1).getId();
        Long thirdOccurrenceId = before.get(2).getId();

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", secondOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-07-14T18:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mockMvc.perform(put("/api/v1/tasks/{id}/recurrence-rule", thirdOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRecurrenceRequest(
                                RecurrenceFrequency.WEEKLY,
                                2,
                                null,
                                null,
                                null,
                                2,
                                List.of("TU"),
                                null
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recurrence.interval").value(2))
                .andExpect(jsonPath("$.data.recurrence.recurrenceCount").value(2));

        RecurrenceSeries updatedSeries = recurrenceSeriesRepository.findById(recurrenceSeriesId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updatedSeries.getInterval()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(updatedSeries.getRecurrenceStartAt()).isEqualTo(LocalDateTime.of(2026, 7, 21, 9, 0));
        org.assertj.core.api.Assertions.assertThat(updatedSeries.getRecurrenceRule()).isEqualTo("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU;COUNT=2");

        List<Task> afterUpdate = taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(
                recurrenceSeriesId,
                owner.getId()
        );
        org.assertj.core.api.Assertions.assertThat(afterUpdate)
                .extracting(Task::getOccurrenceDate)
                .containsExactly(LocalDate.of(2026, 7, 7), LocalDate.of(2026, 7, 14), LocalDate.of(2026, 7, 21));
        org.assertj.core.api.Assertions.assertThat(afterUpdate.get(1).getStatus()).isEqualTo(TaskStatus.DONE);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-08-04"));
    }

    @Test
    @DisplayName("v1 반복 Task 전체 수정은 기존 완료 occurrence의 완료 기록을 보존한다")
    void recurrenceScope_updateAll_preservesCompletedOccurrence() throws Exception {
        String accessToken = accessToken("task-recurrence-preserve-done@example.com");
        User owner = userRepository.findByEmail("task-recurrence-preserve-done@example.com").orElseThrow();
        RecurrenceSeries series = recurrenceSeriesRepository.save(new RecurrenceSeries(
                owner,
                RecurrenceFrequency.WEEKLY,
                1,
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO;COUNT=3",
                "Asia/Seoul",
                LocalDateTime.of(2026, 7, 6, 9, 0),
                null,
                3
        ));
        taskRepository.save(Task.builder()
                .title("반복 회의")
                .description("주간 싱크")
                .type(TaskType.SCHEDULE)
                .startAt(LocalDateTime.of(2026, 7, 6, 9, 0))
                .endAt(LocalDateTime.of(2026, 7, 6, 10, 0))
                .category("업무")
                .owner(owner)
                .recurrenceSeries(series)
                .occurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .originalOccurrenceDate(java.time.LocalDate.of(2026, 7, 6))
                .build());

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("type", "MONTH")
                        .param("taskType", "SCHEDULE")
                        .param("date", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        List<Task> occurrences = taskRepository.findByRecurrenceSeriesIdAndOwnerIdOrderByOccurrenceDateAscIdAsc(series.getId(), owner.getId());
        Long firstOccurrenceId = occurrences.get(0).getId();
        Long secondOccurrenceId = occurrences.get(1).getId();

        mockMvc.perform(patch("/api/v1/tasks/{id}/done", secondOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("completedAt", "2026-07-13T18:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        TaskRequest updateRequest = new TaskRequest(
                "전체 변경 회의",
                "완료 보존",
                TaskType.SCHEDULE,
                LocalDateTime.of(2026, 7, 6, 11, 0),
                LocalDateTime.of(2026, 7, 6, 12, 0),
                "변경",
                false
        );

        mockMvc.perform(put("/api/v1/tasks/{id}", firstOccurrenceId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("recurrenceScope", "ALL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("전체 변경 회의"));

        mockMvc.perform(get("/api/v1/tasks/done")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-07-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(secondOccurrenceId))
                .andExpect(jsonPath("$.data[0].title").value("전체 변경 회의"))
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].completedAt").value("2026-07-13T18:00:00"))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-07-13T11:00:00"))
                .andExpect(jsonPath("$.data[0].occurrenceDate").value("2026-07-13"));
    }

    @Test
    @DisplayName("v1 Task 삭제 응답은 data null envelope를 반환한다")
    void deleteTask_success_dataNull() throws Exception {
        String accessToken = accessToken("task-delete@example.com");
        Long taskId = createTask(accessToken, new TaskRequest("삭제 대상", null, null, null, null, false));

        mockMvc.perform(delete("/api/v1/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.timestamp").value(notNullValue()));
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Task 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createTask(String accessToken, TaskRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long createDdayGoal(String accessToken, String title, String targetDate) throws Exception {
        DdayGoalRequest request = new DdayGoalRequest(title, LocalDate.parse(targetDate));
        String response = mockMvc.perform(post("/api/v1/dday-goals")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }

    private Long moveToToday(String accessToken, Long taskId, String date) throws Exception {
        String response = mockMvc.perform(patch("/api/v1/tasks/{id}/today", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", date))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }
}
