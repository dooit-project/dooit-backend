package com.todolab.config;

import com.todolab.mail.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("OpenAPI JSON에 v1 Task 문서 tag, summary, security, 오류 응답 schema가 노출된다")
    void apiDocs_v1TaskDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.components.schemas.ApiResponse.description").value("공통 API 응답 envelope"))
                .andExpect(jsonPath("$.components.schemas.ErrorBody.description").value("공통 API 오류 응답 본문"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.tags[0]").value("v1 Task"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.summary").value("Task 범위 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.responses['401'].description").value("인증 필요"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/tasks'].get.responses['401'].content['application/json'].schema.$ref"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].post.parameters[*].name")
                        .value(hasItem(CorsConfig.IDEMPOTENCY_KEY_HEADER)))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].post.responses['409'].description")
                        .value("Idempotency-Key가 다른 요청 본문으로 재사용됨"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.tags[0]").value("v1 Task"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.summary").value("Task 통합 검색"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tasks/today-order'].put.tags[0]").value("v1 Task"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/today-order'].put.summary").value("Today Task 일괄 재정렬"));
    }

    @Test
    @DisplayName("OpenAPI JSON에 v1 Auth와 D-Day 문서 tag와 summary가 노출된다")
    void apiDocs_v1AuthAndDdayDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.tags[0]").value("v1 Auth"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").value("로그인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset/request'].post.summary")
                        .value("비밀번호 재설정 요청"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset/verify'].post.summary")
                        .value("비밀번호 재설정 token 검증"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/password-reset/confirm'].post.summary")
                        .value("비밀번호 재설정 확정"))
                .andExpect(jsonPath("$.paths['/api/v1/dday-goals'].post.tags[0]").value("v1 D-Day"))
                .andExpect(jsonPath("$.paths['/api/v1/dday-goals'].post.summary").value("D-Day 목표 생성"))
                .andExpect(jsonPath("$.paths['/api/v1/dday-goals'].post.responses['404'].description")
                        .value("D-Day 목표 없음"));
    }

    @Test
    @DisplayName("OpenAPI JSON에 v1 System metadata 문서가 노출된다")
    void apiDocs_v1SystemMetadataDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/system/metadata'].get.tags[0]").value("v1 System"))
                .andExpect(jsonPath("$.paths['/api/v1/system/metadata'].get.summary").value("백엔드 metadata 조회"))
                .andExpect(jsonPath("$.components.schemas.AppMetadataResponse.description").value("백엔드 배포 metadata"))
                .andExpect(jsonPath("$.tags[*].name").value(hasItem("v1 System")));
    }

    @Test
    @DisplayName("OpenAPI JSON에 v1 Workspace path가 노출된다")
    void apiDocs_v1WorkspacePaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces'].get.tags[0]").value("v1 Workspace"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces'].post.summary").value("Workspace 생성"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces'].post.parameters[*].name")
                        .value(hasItem(CorsConfig.IDEMPOTENCY_KEY_HEADER)))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces'].get.responses['200'].content['*/*'].schema.$ref")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspace-invitations'].get.tags[0]").value("v1 Workspace Invitation"))
                .andExpect(jsonPath("$.paths['/api/v1/workspace-invitations'].get.summary").value("현재 사용자 Workspace 초대 목록"))
                .andExpect(jsonPath("$.paths['/api/v1/workspace-invitations'].get.responses['200'].content['*/*'].schema.$ref")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/members'].post.tags[0]").value("v1 Workspace"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/members'].post.parameters[*].name")
                        .value(hasItem(CorsConfig.IDEMPOTENCY_KEY_HEADER)))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/members'].post.responses['201'].content")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/members/{memberId}'].patch.summary").value("Workspace 멤버 수정"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks'].get.tags[0]").value("v1 Workspace Task"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/notification-candidates'].get.summary")
                        .value("Workspace 로컬 알림 후보 조회"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}'].put.parameters[2].name")
                        .value("recurrenceScope"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/tasks/{taskId}'].delete.parameters[2].schema.enum[0]")
                        .value("THIS"))
                .andExpect(jsonPath("$.paths['/api/v1/workspaces/{workspaceId}/dday-goals'].get.tags[0]")
                        .value("v1 Workspace D-Day"));
    }

    @Test
    @DisplayName("OpenAPI JSON에 request schema의 enum, 날짜 형식, validation 제약이 노출된다")
    void apiDocs_requestSchemaConstraints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.TaskRequest.description").value("Task 생성/수정 요청"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.description").value("Task 응답"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.properties.status.enum[0]").value("INBOX"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.properties.createdAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.properties.recurrenceSeriesId.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.properties.occurrenceDate.format").value("date"))
                .andExpect(jsonPath("$.components.schemas.TaskResponse.properties.recurrenceException.enum[0]").value("SKIPPED"))
                .andExpect(jsonPath("$.components.schemas.TaskRequest.properties.title.maxLength").value(30))
                .andExpect(jsonPath("$.components.schemas.TaskRequest.properties.description.maxLength").value(300))
                .andExpect(jsonPath("$.components.schemas.TaskRequest.properties.type.enum[0]").value("SCHEDULE"))
                .andExpect(jsonPath("$.components.schemas.TaskRequest.properties.startAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.TaskRequest.properties.startAt.example").value("2026-07-15T09:00:00"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.minLength").value(8))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.password.maxLength").value(72))
                .andExpect(jsonPath("$.components.schemas.PasswordResetConfirmRequest.properties.newPassword.minLength")
                        .value(8))
                .andExpect(jsonPath("$.components.schemas.PasswordResetConfirmRequest.properties.newPassword.maxLength")
                        .value(72))
                .andExpect(jsonPath("$.components.schemas.DdayGoalRequest.properties.targetDate.format").value("date"))
                .andExpect(jsonPath("$.components.schemas.DdayGoalResponse.description").value("D-Day 목표 응답"))
                .andExpect(jsonPath("$.components.schemas.DdayGoalResponse.properties.createdAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.DdayGoalTaskRequest.properties.date.example").value("2026-07-15"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.parameters[0].schema.enum[0]").value("DAY"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks'].get.parameters[1].schema.enum[1]").value("SCHEDULE"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.parameters[1].schema.enum[0]").value("INBOX"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.parameters[*].name").value(hasItem("hasRecurrence")))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/search'].get.parameters[*].name").value(hasItem("dateField")))
                .andExpect(jsonPath("$.components.schemas.TodayOrderRequest.description").value("Today Task 일괄 재정렬 요청"))
                .andExpect(jsonPath("$.components.schemas.TodayOrderRequest.required[0]").value("date"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/today-order'].patch.parameters[2].schema.enum[0]").value("UP"))
                .andExpect(jsonPath("$.paths['/api/v1/tasks/{id}/defer-reason'].patch.parameters[1].schema.enum[1]")
                        .value("NOT_NEEDED_NOW"));
    }

    @Test
    @DisplayName("Swagger UI와 Scalar가 v1 OpenAPI 원본과 tag 순서를 읽을 수 있다")
    void documentationUi_v1ApiDocsAndTagOrder() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("v1 Workspace Invitation"))
                .andExpect(jsonPath("$.tags[1].name").value("v1 Calendar Feed"))
                .andExpect(jsonPath("$.tags[2].name").value("v1 Workspace Task"))
                .andExpect(jsonPath("$.tags[3].name").value("v1 Task Template"))
                .andExpect(jsonPath("$.tags[4].name").value("v1 System"))
                .andExpect(jsonPath("$.tags[5].name").value("v1 Workspace"))
                .andExpect(jsonPath("$.tags[6].name").value("v1 Auth"))
                .andExpect(jsonPath("$.tags[*].name").value(hasItem("v1 Workspace")))
                .andExpect(jsonPath("$.tags[*].name").value(hasItem("v1 Calendar Feed")))
                .andExpect(jsonPath("$.tags[*].name").value(hasItem("v1 System")))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/scalar.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-url=\"/v3/api-docs\"")));
    }

    @Test
    @DisplayName("OpenAPI JSON은 v1 D-Day와 legacy D-Day path를 다른 tag로 구분한다")
    void apiDocs_v1AndLegacyDdayPathsAreSeparated() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/dday-goals/{id}'].get.tags[0]").value("v1 D-Day"))
                .andExpect(jsonPath("$.paths['/api/ddays/{id}'].delete.tags[0]").value("dday-goal-controller"))
                .andExpect(jsonPath("$.paths['/api/ddays/{id}'].get").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/ddays/{id}/tasks'].post").doesNotExist());
    }
}
