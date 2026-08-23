package com.todolab.config;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백엔드 배포 metadata")
public record AppMetadataResponse(
        @Schema(description = "애플리케이션 버전", example = "1.0-SNAPSHOT")
        String version,
        @Schema(description = "배포된 backend commit SHA 또는 image revision", example = "b292654")
        String commitSha,
        @Schema(description = "배포된 Docker image tag", example = "b292654")
        String imageTag
) {

    public static AppMetadataResponse from(AppMetadataProperties properties) {
        return new AppMetadataResponse(properties.version(), properties.commitSha(), properties.imageTag());
    }
}
