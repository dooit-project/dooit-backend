package com.todolab.task.controller;

import com.jayway.jsonpath.JsonPath;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.mail.MailService;
import com.todolab.task.domain.RecurrenceFrequency;
import com.todolab.task.domain.TaskType;
import com.todolab.task.dto.TaskTemplateCreateTaskRequest;
import com.todolab.task.dto.TaskTemplateRequest;
import com.todolab.task.repository.TaskTemplateRepository;
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
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskTemplateV1IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TaskTemplateRepository taskTemplateRepository;

    @Autowired
    JwtTokenService jwtTokenService;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("v1 Task 템플릿 CRUD는 로그인 사용자 범위로 동작한다")
    void templateCrud_ownerScoped() throws Exception {
        String accessToken = accessToken("task-template-crud@example.com");
        TaskTemplateRequest createRequest = new TaskTemplateRequest(
                "운동",
                "헬스장",
                TaskType.SCHEDULE,
                "건강",
                false,
                LocalTime.of(9, 0),
                90,
                RecurrenceFrequency.WEEKLY,
                1,
                List.of("MO")
        );

        Long templateId = createTemplate(accessToken, createRequest);

        mockMvc.perform(get("/api/v1/task-templates")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(templateId))
                .andExpect(jsonPath("$.data[0].title").value("운동"))
                .andExpect(jsonPath("$.data[0].defaultStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.data[0].recurrenceFrequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data[0].recurrenceByDays[0]").value("MO"));

        TaskTemplateRequest updateRequest = new TaskTemplateRequest(
                "아침 운동",
                "스트레칭",
                TaskType.SCHEDULE,
                "건강",
                false,
                LocalTime.of(8, 30),
                60,
                RecurrenceFrequency.WEEKLY,
                1,
                List.of("MO")
        );

        mockMvc.perform(put("/api/v1/task-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("아침 운동"))
                .andExpect(jsonPath("$.data.description").value("스트레칭"))
                .andExpect(jsonPath("$.data.defaultStartTime").value("08:30:00"));

        mockMvc.perform(get("/api/v1/task-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(templateId))
                .andExpect(jsonPath("$.data.title").value("아침 운동"));

        String otherAccessToken = accessToken("task-template-other@example.com");
        mockMvc.perform(get("/api/v1/task-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + otherAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(20003));

        mockMvc.perform(delete("/api/v1/task-templates/{id}", templateId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        org.assertj.core.api.Assertions.assertThat(taskTemplateRepository.findById(templateId)).isEmpty();
    }

    @Test
    @DisplayName("v1 Task 템플릿으로 반복 일정을 생성한다")
    void createTaskFromTemplate_success() throws Exception {
        String accessToken = accessToken("task-template-create-task@example.com");
        Long templateId = createTemplate(accessToken, new TaskTemplateRequest(
                "운동",
                "헬스장",
                TaskType.SCHEDULE,
                "건강",
                false,
                LocalTime.of(9, 0),
                90,
                RecurrenceFrequency.WEEKLY,
                1,
                List.of("MO")
        ));
        TaskTemplateCreateTaskRequest request = new TaskTemplateCreateTaskRequest(
                LocalDate.of(2026, 8, 17),
                "월요일 운동",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/task-templates/{id}/tasks", templateId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.title").value("월요일 운동"))
                .andExpect(jsonPath("$.data.description").value("헬스장"))
                .andExpect(jsonPath("$.data.type").value("SCHEDULE"))
                .andExpect(jsonPath("$.data.startAt").value("2026-08-17T09:00:00"))
                .andExpect(jsonPath("$.data.endAt").value("2026-08-17T10:30:00"))
                .andExpect(jsonPath("$.data.category").value("건강"))
                .andExpect(jsonPath("$.data.recurrenceSeriesId").value(notNullValue()))
                .andExpect(jsonPath("$.data.recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.data.recurrence.recurrenceRule").value("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO"));
    }

    private String accessToken(String email) {
        User owner = userRepository.save(new User(email, "encoded-password", "Task 템플릿 사용자"));
        return jwtTokenService.createAccessToken(owner).tokenValue();
    }

    private Long createTemplate(String accessToken, TaskTemplateRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/task-templates")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.data.id");
        return id.longValue();
    }
}
