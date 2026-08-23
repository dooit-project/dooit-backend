package com.todolab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI toDoLabOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ToDoLab API")
                        .version("v1")
                        .description("ToDoLab 모바일과 웹 클라이언트가 사용하는 REST API 계약입니다."))
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
                        new Tag().name("v1 System").description("운영 확인용 공개 metadata API")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
