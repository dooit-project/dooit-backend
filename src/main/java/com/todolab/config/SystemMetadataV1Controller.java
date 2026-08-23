package com.todolab.config;

import com.todolab.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "v1 System", description = "운영 확인용 공개 metadata API")
public class SystemMetadataV1Controller {

    private final AppMetadataProperties appMetadataProperties;

    @Operation(summary = "백엔드 metadata 조회", description = "현재 배포된 backend version, commit SHA, image tag를 반환합니다.")
    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<AppMetadataResponse>> metadata() {
        return ResponseEntity.ok(ApiResponse.success(AppMetadataResponse.from(appMetadataProperties)));
    }
}
