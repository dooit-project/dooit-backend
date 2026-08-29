package pj.dooit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final Set<String> IDEMPOTENT_POST_PATHS = Set.of(
            "/api/v1/auth/guest",
            "/api/v1/tasks",
            "/api/v1/tasks/quick-capture",
            "/api/v1/task-templates",
            "/api/v1/task-templates/{id}/tasks",
            "/api/v1/dday-goals",
            "/api/v1/dday-goals/{id}/tasks",
            "/api/v1/workspaces",
            "/api/v1/workspaces/{workspaceId}/members",
            "/api/v1/workspaces/{workspaceId}/tasks",
            "/api/v1/workspaces/{workspaceId}/dday-goals"
    );

    @Bean
    public OpenAPI dooitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dooit API")
                        .version("v1")
                        .description("Dooit 모바일과 웹 클라이언트가 사용하는 REST API 계약입니다."))
                .servers(List.of(new Server()
                        .url("/")
                        .description("Current host")))
                .tags(List.of(
                        new Tag().name("v1 Auth").description("모바일 JWT 인증 API"),
                        new Tag().name("v1 Task").description("모바일 Task API"),
                        new Tag().name("v1 Workspace").description("공유 workspace API"),
                        new Tag().name("v1 Workspace Invitation").description("현재 사용자 workspace 초대 API"),
                        new Tag().name("v1 Workspace Task").description("공유 workspace Task API"),
                        new Tag().name("v1 Workspace D-Day").description("공유 workspace D-Day API"),
                        new Tag().name("v1 D-Day").description("모바일 D-Day 목표 API"),
                        new Tag().name("v1 Calendar Feed").description("iCalendar 읽기 전용 feed API"),
                        new Tag().name("v1 System").description("운영 확인용 공개 metadata API")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OpenApiCustomizer idempotencyOpenApiCustomizer() {
        return openApi -> IDEMPOTENT_POST_PATHS.forEach(path -> {
            if (openApi.getPaths() == null || !openApi.getPaths().containsKey(path)) {
                return;
            }
            Operation post = openApi.getPaths().get(path).getPost();
            if (post == null) {
                return;
            }
            post.addParametersItem(new Parameter()
                    .in("header")
                    .name(CorsConfig.IDEMPOTENCY_KEY_HEADER)
                    .required(false)
                    .description("생성 요청 멱등성 key. 같은 key와 같은 payload는 최초 응답을 replay합니다.")
                    .schema(new StringSchema().maxLength(160)));
            post.getResponses().addApiResponse(
                    "409",
                    new ApiResponse().description("Idempotency-Key가 다른 요청 본문으로 재사용됨")
            );
        });
    }
}
