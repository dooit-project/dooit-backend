package pj.dooit.task.controller;

import pj.dooit.common.api.ApiExceptionHandler;
import pj.dooit.common.api.ErrorCode;
import pj.dooit.config.CorsConfig;
import pj.dooit.dday.exception.DdayGoalNotFoundException;
import pj.dooit.task.domain.DeferReason;
import pj.dooit.task.domain.TaskStatus;
import pj.dooit.task.domain.TaskType;
import pj.dooit.task.domain.TodayOrderDirection;
import pj.dooit.task.dto.TaskCategoryGroupResponse;
import pj.dooit.task.dto.TaskRequest;
import pj.dooit.task.dto.TaskRecommendationResponse;
import pj.dooit.task.dto.TaskResponse;
import pj.dooit.task.exception.TaskNotFoundException;
import pj.dooit.task.service.TaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({ApiExceptionHandler.class, CorsConfig.class})
@WebMvcTest(controllers = TaskController.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:8081,http://localhost:8090")
class TaskControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TaskService taskService;

    /*******************
     *  일정 등록
     *******************/
    @Test
    @DisplayName("일정 등록 성공")
    void createTask_success() throws Exception {
        // given
        TaskRequest req = new TaskRequest(
                "테스트 코드 작성",
                "까먹지 말자..!",
                LocalDateTime.of(2025, 11, 18, 10, 42),
                LocalDateTime.of(2025, 11, 18, 11, 42),
                "공부",
                false
        );

        TaskResponse mockRes = TaskResponse.builder()
                .id(1L)
                .title("테스트 코드 작성")
                .description("까먹지 말자..!")
                .startAt(LocalDateTime.of(2025, 11, 18, 10, 42))
                .endAt(LocalDateTime.of(2025, 11, 18, 11, 42))
                .allDay(false)
                .unscheduled(false)
                .category("공부")
                .createdAt(null)
                .build();

        given(taskService.create(any(TaskRequest.class))).willReturn(mockRes);

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("테스트 코드 작성"))
                .andExpect(jsonPath("$.data.description").value("까먹지 말자..!"))
                .andExpect(jsonPath("$.data.startAt").value("2025-11-18T10:42:00"))
                .andExpect(jsonPath("$.data.endAt").value("2025-11-18T11:42:00"))
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.category").value("공부"));

        then(taskService).should().create(any(TaskRequest.class));
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 등록 실패 - title은 필수이며 없을 경우 400, 10001 에러를 반환한다")
    void createTask_fail_titleMissing() throws Exception {
        // given
        TaskRequest req = new TaskRequest(
                null,
                "desc",
                null,
                null,
                null,
                false
        );

        // when & then
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    /*******************
     *  일정 조회 (단건)
     *******************/
    @Test
    @DisplayName("일정 단건 조회 성공")
    void getTask_success() throws Exception {
        // given
        long id = 11L;

        TaskResponse resp = TaskResponse.builder()
                .id(11L)
                .title("테스트")
                .description("설명")
                .startAt(LocalDateTime.of(2025, 12, 15, 10, 0))
                .endAt(null)
                .allDay(false)
                .unscheduled(false)
                .category("일")
                .createdAt(null)
                .build();

        given(taskService.getTask(id)).willReturn(resp);

        // when & then
        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("테스트"))
                .andExpect(jsonPath("$.data.description").value("설명"))
                .andExpect(jsonPath("$.data.startAt").value("2025-12-15T10:00:00"))
                .andExpect(jsonPath("$.data.endAt").doesNotExist()) // endAt null이면 직렬화 정책에 따라 null로 나올 수도 있음
                .andExpect(jsonPath("$.data.allDay").value(false))
                .andExpect(jsonPath("$.data.category").value("일"));

        then(taskService).should().getTask(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 단건 조회 실패 - 존재하지 않으면 404와 TASK_NOT_FOUND를 반환한다")
    void getTask_taskNotFound() throws Exception {
        // given
        long id = 999L;

        given(taskService.getTask(id)).willThrow(new TaskNotFoundException(id));

        // when & then
        mockMvc.perform(get("/api/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.TASK_NOT_FOUND.getCode()));

        then(taskService).should().getTask(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 단건 조회 실패 - PathVariable 타입이 잘못되면 400 에러를 반환한다")
    void getTask_invalidPathVariable() throws Exception {
        mockMvc.perform(get("/api/tasks/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    /*******************
     *  일정 조회 (DAY / WEEK / MONTH)
     *******************/
    @Test
    @DisplayName("일정 조회 성공 - DAY 타입으로 정상 조회된다")
    void getTasks_DAY_success() throws Exception {
        // given
        List<TaskResponse> dummy = List.of(
                TaskResponse.builder()
                        .id(999L)
                        .title("일정 조회 DAY")
                        .description("일정 조회")
                        .startAt(LocalDateTime.of(2025, 11, 25, 10, 30))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build()
        );

        given(taskService.getTasks(any())).willReturn(dummy);

        // when & then
        mockMvc.perform(get("/api/tasks")
                        .param("type", "DAY")
                        .param("date", "2025-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("일"))
                .andExpect(jsonPath("$.data[0].id").value(999))
                .andExpect(jsonPath("$.data[0].title").value("일정 조회 DAY"))
                .andExpect(jsonPath("$.data[0].startAt").value("2025-11-25T10:30:00"));

        then(taskService).should().getTasks(any());
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 조회 성공 - WEEK 타입으로 정상 조회된다")
    void getTasks_WEEK_success() throws Exception {
        // given
        List<TaskResponse> dummy = List.of(
                TaskResponse.builder()
                        .id(11L)
                        .title("WEEK 일정 1")
                        .description("설명1")
                        .startAt(LocalDateTime.of(2025, 11, 24, 1, 0))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build(),
                TaskResponse.builder()
                        .id(21L)
                        .title("WEEK 일정 2")
                        .description("설명2")
                        .startAt(LocalDateTime.of(2025, 11, 30, 23, 0))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build()
        );

        given(taskService.getTasks(any())).willReturn(dummy);

        // when & then
        mockMvc.perform(get("/api/tasks")
                        .param("type", "WEEK")
                        .param("date", "2025-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].category").value("일"))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[1].id").value(21));

        then(taskService).should().getTasks(any());
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 조회 성공 - MONTH 타입으로 정상 조회된다")
    void getTasks_MONTH_success() throws Exception {
        // given
        List<TaskResponse> dummy = List.of(
                TaskResponse.builder()
                        .id(111L)
                        .title("MONTH 일정 1")
                        .description("설명1")
                        .startAt(LocalDateTime.of(2025, 11, 3, 8, 0))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build(),
                TaskResponse.builder()
                        .id(112L)
                        .title("MONTH 일정 2")
                        .description("설명2")
                        .startAt(LocalDateTime.of(2025, 11, 10, 9, 30))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build(),
                TaskResponse.builder()
                        .id(113L)
                        .title("MONTH 일정 3")
                        .description("설명3")
                        .startAt(LocalDateTime.of(2025, 11, 18, 14, 0))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build(),
                TaskResponse.builder()
                        .id(114L)
                        .title("MONTH 일정 4")
                        .description("설명4")
                        .startAt(LocalDateTime.of(2025, 11, 28, 19, 45))
                        .endAt(null)
                        .allDay(false)
                        .unscheduled(false)
                        .category("일")
                        .createdAt(null)
                        .build()
        );

        given(taskService.getTasks(any())).willReturn(dummy);

        // when & then
        mockMvc.perform(get("/api/tasks")
                        .param("type", "MONTH")
                        .param("date", "2025-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].category").value("일"))
                .andExpect(jsonPath("$.data[0].id").value(111))
                .andExpect(jsonPath("$.data[0].title").value("MONTH 일정 1"))
                .andExpect(jsonPath("$.data[1].id").value(112))
                .andExpect(jsonPath("$.data[1].title").value("MONTH 일정 2"))
                .andExpect(jsonPath("$.data[2].id").value(113))
                .andExpect(jsonPath("$.data[2].title").value("MONTH 일정 3"))
                .andExpect(jsonPath("$.data[3].id").value(114))
                .andExpect(jsonPath("$.data[3].title").value("MONTH 일정 4"));

        then(taskService).should().getTasks(any());
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("카테고리 그룹 일정 조회 성공")
    void getGroupedTasks_success() throws Exception {
        TaskResponse task = TaskResponse.builder()
                .id(1L)
                .title("grouped")
                .category("일")
                .startAt(LocalDateTime.of(2025, 11, 25, 10, 0))
                .build();

        given(taskService.getGroupedTasks(any()))
                .willReturn(List.of(new TaskCategoryGroupResponse("일", List.of(task))));

        mockMvc.perform(get("/api/tasks/grouped")
                        .param("type", "DAY")
                        .param("date", "2025-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].category").value("일"))
                .andExpect(jsonPath("$.data[0].tasks[0].id").value(1))
                .andExpect(jsonPath("$.data[0].tasks[0].title").value("grouped"));

        then(taskService).should().getGroupedTasks(any());
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("카테고리 그룹 일정 조회 실패 - 잘못된 type이면 400, 10001 에러를 반환한다")
    void getGroupedTasks_fail_invalidType() throws Exception {
        mockMvc.perform(get("/api/tasks/grouped")
                        .param("type", "INVALID")
                        .param("date", "2025-11-25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("카테고리 그룹 일정 조회 실패 - 잘못된 taskType이면 400, 10001 에러를 반환한다")
    void getGroupedTasks_fail_invalidTaskType() throws Exception {
        mockMvc.perform(get("/api/tasks/grouped")
                        .param("type", "DAY")
                        .param("taskType", "INVALID")
                        .param("date", "2025-11-25"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Inbox 조회 성공")
    void getInboxTasks_success() throws Exception {
        // given
        TaskResponse inbox = TaskResponse.builder()
                .id(1L)
                .title("inbox")
                .status(TaskStatus.INBOX)
                .build();

        given(taskService.getInboxTasks()).willReturn(List.of(inbox));

        // when & then
        mockMvc.perform(get("/api/tasks/inbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("inbox"))
                .andExpect(jsonPath("$.data[0].status").value("INBOX"));

        then(taskService).should().getInboxTasks();
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Today 추천 조회 성공")
    void getTodayRecommendations_success() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 6, 16);
        TaskResponse task = TaskResponse.builder()
                .id(1L)
                .title("추천 할 일")
                .status(TaskStatus.INBOX)
                .build();
        TaskRecommendationResponse recommendation = new TaskRecommendationResponse(task, "최근 기록");

        given(taskService.getTodayRecommendations(date)).willReturn(List.of(recommendation));

        // when & then
        mockMvc.perform(get("/api/tasks/recommendations/today")
                        .param("date", "2026-06-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reason").value("최근 기록"))
                .andExpect(jsonPath("$.data[0].task.id").value(1))
                .andExpect(jsonPath("$.data[0].task.title").value("추천 할 일"))
                .andExpect(jsonPath("$.data[0].task.status").value("INBOX"));

        then(taskService).should().getTodayRecommendations(date);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Today 추천 조회 성공 - 모바일 클라이언트 경로도 지원한다")
    void getTodayRecommendations_mobileAlias_success() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 6, 16);
        given(taskService.getTodayRecommendations(date)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/tasks/today/recommendations")
                        .param("date", "2026-06-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(0));

        then(taskService).should().getTodayRecommendations(date);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Today 조회 성공")
    void getTodayTasks_success() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 5, 21);
        TaskResponse today = TaskResponse.builder()
                .id(1L)
                .title("today")
                .status(TaskStatus.TODAY)
                .targetDate(date)
                .build();

        given(taskService.getTodayTasks(date)).willReturn(List.of(today));

        // when & then
        mockMvc.perform(get("/api/tasks/today")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("TODAY"))
                .andExpect(jsonPath("$.data[0].targetDate").value("2026-05-21"));

        then(taskService).should().getTodayTasks(date);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Expo Web Origin은 API CORS 응답 헤더를 받는다")
    void cors_expoWebOrigin_success() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 21);
        given(taskService.getTodayTasks(date)).willReturn(List.of());

        mockMvc.perform(get("/api/tasks/today")
                        .header("Origin", "http://localhost:8081")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(0));

        then(taskService).should().getTodayTasks(date);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8081", "http://localhost:8090"})
    @DisplayName("local Expo Web 인증 요청의 CORS preflight를 허용한다")
    void cors_authorizationPreflight_success(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/tasks/today")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.AUTHORIZATION)
                ));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("지난 미완료 조회 성공")
    void getOverdueTasks_success() throws Exception {
        // given
        LocalDate referenceDate = LocalDate.of(2026, 5, 21);
        LocalDate targetDate = referenceDate.minusDays(2);
        TaskResponse overdue = TaskResponse.builder()
                .id(1L)
                .title("overdue")
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .build();

        given(taskService.getOverdueTasks(referenceDate)).willReturn(List.of(overdue));

        // when & then
        mockMvc.perform(get("/api/tasks/overdue")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("TODAY"))
                .andExpect(jsonPath("$.data[0].targetDate").value("2026-05-19"));

        then(taskService).should().getOverdueTasks(referenceDate);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("지난 미완료 조회 성공 - stale 경로와 date 파라미터를 지원한다")
    void getStaleTasks_success() throws Exception {
        // given
        LocalDate referenceDate = LocalDate.of(2026, 5, 21);
        given(taskService.getOverdueTasks(referenceDate)).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/tasks/stale")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(0));

        then(taskService).should().getOverdueTasks(referenceDate);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Done 조회 성공")
    void getDoneTasks_success() throws Exception {
        // given
        LocalDate date = LocalDate.of(2026, 5, 21);
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 21, 22, 0);
        TaskResponse done = TaskResponse.builder()
                .id(1L)
                .title("done")
                .status(TaskStatus.DONE)
                .completedAt(completedAt)
                .build();

        given(taskService.getDoneTasks(date)).willReturn(List.of(done));

        // when & then
        mockMvc.perform(get("/api/tasks/done")
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].completedAt").value("2026-05-21T22:00:00"));

        then(taskService).should().getDoneTasks(date);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Today 조회 실패 - date 형식이 잘못되면 400 에러를 반환한다")
    void getTodayTasks_fail_invalidDate() throws Exception {
        mockMvc.perform(get("/api/tasks/today")
                        .param("date", "20260521"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("지난 미완료 조회 실패 - date 형식이 잘못되면 400 에러를 반환한다")
    void getOverdueTasks_fail_invalidDate() throws Exception {
        mockMvc.perform(get("/api/tasks/overdue")
                        .param("date", "20260521"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 조회 실패 - 잘못된 type이면 400, 10001 에러를 반환한다")
    void getTasks_fail_invalidType() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "INVALID")
                        .param("date", "2025-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 조회 실패 - 잘못된 taskType이면 400, 10001 에러를 반환한다")
    void getTasks_fail_invalidTaskType() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "DAY")
                        .param("taskType", "INVALID")
                        .param("date", "2025-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 조회 실패 - type이 누락되면 400, 10001 에러를 반환한다")
    void getTasks_fail_missingType() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("date", "2025-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 조회 실패 - date가 누락되면 400, 10001 에러를 반환한다")
    void getTasks_fail_missingDate() throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "DAY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-11", "20251127", "25-11-27"})
    @DisplayName("일정 조회 실패 - DAY는 yyyy-MM-dd 형식을 요구한다")
    void getTasks_DAY_fail_invalidDateFormat(String invalidDate) throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "DAY")
                        .param("date", invalidDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-11", "20251127", "25-11-27"})
    @DisplayName("일정 조회 실패 - WEEK는 yyyy-MM-dd 형식을 요구한다")
    void getTasks_WEEK_fail_invalidDateFormat(String invalidDate) throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "WEEK")
                        .param("date", invalidDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-11-27", "202511", "25-11"})
    @DisplayName("일정 조회 실패 - MONTH는 yyyy-MM 형식을 요구한다")
    void getTasks_MONTH_fail_invalidDateFormat(String invalidDate) throws Exception {
        mockMvc.perform(get("/api/tasks")
                        .param("type", "MONTH")
                        .param("date", invalidDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    /*******************
     *  미정 일정 조회
     *******************/
    @Test
    @DisplayName("미정 일정 조회")
    void getUnscheduledTasks() throws Exception {
        TaskResponse t1 = TaskResponse.builder()
                .id(1L)
                .title("u1")
                .startAt(null)
                .endAt(null)
                .unscheduled(true)
                .build();

        TaskResponse t2 = TaskResponse.builder()
                .id(2L)
                .title("u2")
                .startAt(null)
                .endAt(null)
                .unscheduled(true)
                .category("WORK")
                .build();

        List<TaskResponse> tasks = List.of(t1, t2);

        given(taskService.getUnscheduledTasks()).willReturn(tasks);

        mockMvc.perform(get("/api/tasks/unscheduled"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].title").value("u1"))
                .andExpect(jsonPath("$.data[0].startAt").isEmpty())
                .andExpect(jsonPath("$.data[0].endAt").isEmpty())
                .andExpect(jsonPath("$.data[0].unscheduled").value(true))
                .andExpect(jsonPath("$.data[1].category").value("WORK"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].title").value("u2"))
                .andExpect(jsonPath("$.data[1].startAt").isEmpty())
                .andExpect(jsonPath("$.data[1].endAt").isEmpty())
                .andExpect(jsonPath("$.data[1].unscheduled").value(true));
    }

    /*******************
     *  일정 수정
     *******************/
    @Test
    @DisplayName("일정 정상 수정")
    void updateTask_success() throws Exception {
        // given
        long id = 10L;

        TaskRequest req = new TaskRequest(
                "updated title",
                null,
                null,
                null,
                "일",
                false
        );

        TaskResponse serviceRes = TaskResponse.builder()
                .id(id)
                .title("updated title2")
                .build();

        given(taskService.update(eq(id), any(TaskRequest.class))).willReturn(serviceRes);

        // when & then
        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.title").value("updated title2"));

        then(taskService).should().update(eq(id), any(TaskRequest.class));
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 수정 실패 - 없는 id일 경우 TaskNotFoundException 발생")
    void updateTask_NotExistId() throws Exception {
        // given
        long id = 10L;

        TaskRequest req = new TaskRequest(
                "title",
                null,
                null,
                null,
                "일",
                false
        );

        given(taskService.update(eq(id), any(TaskRequest.class)))
                .willThrow(new TaskNotFoundException(id));

        // when & then
        mockMvc.perform(put("/api/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.TASK_NOT_FOUND.getCode()));

        then(taskService).should().update(eq(id), any(TaskRequest.class));
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 수정 실패 - PathVariable 타입이 잘못되면 400 에러를 반환한다")
    void updateTask_invalidPathVariable() throws Exception {
        TaskRequest req = new TaskRequest(
                "title",
                null,
                null,
                null,
                "일",
                false
        );

        mockMvc.perform(put("/api/tasks/{id}", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Today 이동 성공")
    void moveToToday_success() throws Exception {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 21);
        TaskResponse moved = TaskResponse.builder()
                .id(id)
                .title("moved")
                .startAt(targetDate.atStartOfDay())
                .endAt(targetDate.plusDays(1).atStartOfDay())
                .allDay(true)
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .build();

        given(taskService.moveToToday(id, targetDate)).willReturn(moved);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/today", id)
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.startAt").value("2026-05-21T00:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-05-22T00:00:00"))
                .andExpect(jsonPath("$.data.allDay").value(true));

        then(taskService).should().moveToToday(id, targetDate);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("기록함 이동 성공")
    void moveToInbox_success() throws Exception {
        // given
        long id = 1L;
        TaskResponse moved = TaskResponse.builder()
                .id(id)
                .title("moved")
                .type(TaskType.TODO)
                .status(TaskStatus.INBOX)
                .build();

        given(taskService.moveToInbox(id)).willReturn(moved);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/inbox", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("INBOX"))
                .andExpect(jsonPath("$.data.startAt").doesNotExist());

        then(taskService).should().moveToInbox(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("완료 처리 성공")
    void complete_success() throws Exception {
        // given
        long id = 1L;
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 21, 22, 0);
        TaskResponse completed = TaskResponse.builder()
                .id(id)
                .title("done")
                .status(TaskStatus.DONE)
                .completedAt(completedAt)
                .build();

        given(taskService.complete(id, completedAt)).willReturn(completed);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/done", id)
                        .param("completedAt", "2026-05-21T22:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-05-21T22:00:00"));

        then(taskService).should().complete(id, completedAt);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("완료 취소 성공")
    void reopenToday_success() throws Exception {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 21);
        TaskResponse reopened = TaskResponse.builder()
                .id(id)
                .title("reopened")
                .startAt(targetDate.atStartOfDay())
                .endAt(targetDate.plusDays(1).atStartOfDay())
                .allDay(true)
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .completedAt(null)
                .build();

        given(taskService.reopenToday(id, targetDate)).willReturn(reopened);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/done/cancel", id)
                        .param("date", "2026-05-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-05-21"))
                .andExpect(jsonPath("$.data.startAt").value("2026-05-21T00:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-05-22T00:00:00"))
                .andExpect(jsonPath("$.data.allDay").value(true))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        then(taskService).should().reopenToday(id, targetDate);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("이월 처리 성공")
    void carryOver_success() throws Exception {
        // given
        long id = 1L;
        LocalDate nextDate = LocalDate.of(2026, 5, 22);
        TaskResponse carriedOver = TaskResponse.builder()
                .id(id)
                .title("carried over")
                .startAt(nextDate.atStartOfDay())
                .endAt(nextDate.plusDays(1).atStartOfDay())
                .allDay(true)
                .status(TaskStatus.TODAY)
                .targetDate(nextDate)
                .carryOverCount(3)
                .staleCarryOver(true)
                .build();

        given(taskService.carryOver(id, nextDate)).willReturn(carriedOver);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/carry-over", id)
                        .param("date", "2026-05-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("TODAY"))
                .andExpect(jsonPath("$.data.targetDate").value("2026-05-22"))
                .andExpect(jsonPath("$.data.carryOverCount").value(3))
                .andExpect(jsonPath("$.data.staleCarryOver").value(true))
                .andExpect(jsonPath("$.data.startAt").value("2026-05-22T00:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-05-23T00:00:00"));

        then(taskService).should().carryOver(id, nextDate);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Today 실행 순서 변경 성공")
    void reorderToday_success() throws Exception {
        // given
        long id = 1L;
        LocalDate targetDate = LocalDate.of(2026, 5, 22);
        TaskResponse reordered = TaskResponse.builder()
                .id(id)
                .title("reordered")
                .status(TaskStatus.TODAY)
                .targetDate(targetDate)
                .todayOrder(2)
                .build();

        given(taskService.reorderToday(id, targetDate, TodayOrderDirection.DOWN)).willReturn(reordered);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/today-order", id)
                        .param("date", "2026-05-22")
                        .param("direction", "DOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.todayOrder").value(2));

        then(taskService).should().reorderToday(id, targetDate, TodayOrderDirection.DOWN);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("D-Day 목표 연결 성공")
    void connectDdayGoal_success() throws Exception {
        // given
        long id = 1L;
        long ddayGoalId = 10L;
        TaskResponse connected = TaskResponse.builder()
                .id(id)
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 31))
                .ddayGoalId(ddayGoalId)
                .ddayGoalTitle("정보처리기사")
                .ddayGoalTargetDate(LocalDate.of(2026, 6, 10))
                .ddayDaysLeft(10L)
                .build();

        given(taskService.connectDdayGoal(id, ddayGoalId)).willReturn(connected);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/dday-goal", id)
                        .param("ddayGoalId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.ddayGoalId").value(10))
                .andExpect(jsonPath("$.data.ddayGoalTitle").value("정보처리기사"))
                .andExpect(jsonPath("$.data.ddayGoalTargetDate").value("2026-06-10"))
                .andExpect(jsonPath("$.data.ddayDaysLeft").value(10));

        then(taskService).should().connectDdayGoal(id, ddayGoalId);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("D-Day 목표 연결 실패 - 존재하지 않는 D-Day 목표면 404를 반환한다")
    void connectDdayGoal_fail_goalNotFound() throws Exception {
        // given
        long id = 1L;
        long ddayGoalId = 99L;
        given(taskService.connectDdayGoal(id, ddayGoalId))
                .willThrow(new DdayGoalNotFoundException(ddayGoalId));

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/dday-goal", id)
                        .param("ddayGoalId", "99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.DDAY_GOAL_NOT_FOUND.getCode()));

        then(taskService).should().connectDdayGoal(id, ddayGoalId);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("D-Day 목표 연결 해제 성공")
    void disconnectDdayGoal_success() throws Exception {
        // given
        long id = 1L;
        TaskResponse disconnected = TaskResponse.builder()
                .id(id)
                .title("기출 20문제 풀기")
                .status(TaskStatus.TODAY)
                .targetDate(LocalDate.of(2026, 5, 31))
                .ddayGoalId(null)
                .build();

        given(taskService.disconnectDdayGoal(id)).willReturn(disconnected);

        // when & then
        mockMvc.perform(delete("/api/tasks/{id}/dday-goal", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.ddayGoalId").doesNotExist());

        then(taskService).should().disconnectDdayGoal(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("미룬 이유 저장 성공")
    void setDeferReason_success() throws Exception {
        // given
        long id = 1L;
        TaskResponse updated = TaskResponse.builder()
                .id(id)
                .title("carried over")
                .status(TaskStatus.TODAY)
                .carryOverCount(3)
                .staleCarryOver(true)
                .deferReason(DeferReason.ETC)
                .deferReasonLabel("기타")
                .build();

        given(taskService.setDeferReason(id, DeferReason.ETC)).willReturn(updated);

        // when & then
        mockMvc.perform(patch("/api/tasks/{id}/defer-reason", id)
                        .param("reason", "ETC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.deferReason").value("ETC"))
                .andExpect(jsonPath("$.data.deferReasonLabel").value("기타"));

        then(taskService).should().setDeferReason(id, DeferReason.ETC);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("미룬 이유 해제 성공")
    void clearDeferReason_success() throws Exception {
        // given
        long id = 1L;
        TaskResponse updated = TaskResponse.builder()
                .id(id)
                .title("carried over")
                .status(TaskStatus.TODAY)
                .carryOverCount(3)
                .staleCarryOver(true)
                .build();

        given(taskService.clearDeferReason(id)).willReturn(updated);

        // when & then
        mockMvc.perform(delete("/api/tasks/{id}/defer-reason", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.deferReason").doesNotExist())
                .andExpect(jsonPath("$.data.deferReasonLabel").doesNotExist());

        then(taskService).should().clearDeferReason(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    /*******************
     *  일정 삭제
     *******************/
    @Test
    @DisplayName("일정 삭제 성공 - 존재하는 id면 200과 null data를 반환한다")
    void deleteTask_success() throws Exception {
        // given
        long id = 1L;
        willDoNothing().given(taskService).delete(id);

        // when & then
        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        then(taskService).should().delete(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 삭제 실패 - 존재하지 않는 id면 404와 TASK_NOT_FOUND를 반환한다")
    void deleteTask_notFound() throws Exception {
        // given
        long id = 999L;
        willThrow(new TaskNotFoundException(id)).given(taskService).delete(id);

        // when & then
        mockMvc.perform(delete("/api/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.TASK_NOT_FOUND.getCode()));

        then(taskService).should().delete(id);
        then(taskService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일정 삭제 실패 - PathVariable 타입이 잘못되면 400 에러를 반환한다")
    void deleteTask_invalidPathVariable() throws Exception {
        mockMvc.perform(delete("/api/tasks/{id}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(taskService).shouldHaveNoInteractions();
    }
}
