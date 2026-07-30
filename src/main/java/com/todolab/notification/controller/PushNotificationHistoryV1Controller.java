package com.todolab.notification.controller;

import com.todolab.auth.service.CurrentUserService;
import com.todolab.common.api.ApiResponse;
import com.todolab.notification.dto.PushNotificationHistoryResponse;
import com.todolab.notification.service.PushNotificationHistoryService;
import com.todolab.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/push-notification-histories")
@RequiredArgsConstructor
@Tag(name = "v1 Push Notification History", description = "모바일 서버 push 알림 전송 이력 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청값 검증 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 필요",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        )
})
public class PushNotificationHistoryV1Controller {

    private final PushNotificationHistoryService pushNotificationHistoryService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "push 알림 전송 이력 조회", description = "로그인 사용자의 최근 서버 push 알림 전송 이력을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PushNotificationHistoryResponse>>> getRecentHistories(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "조회 개수. 1 이상 100 이하", schema = @Schema(example = "50", minimum = "1", maximum = "100"))
            @RequestParam(required = false) Integer limit
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(pushNotificationHistoryService.getRecentHistoriesForOwner(owner, limit)));
    }
}
