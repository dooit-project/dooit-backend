package pj.dooit.notification.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.notification.dto.PushDeviceTokenRequest;
import pj.dooit.notification.dto.PushDeviceTokenResponse;
import pj.dooit.notification.service.PushDeviceTokenService;
import pj.dooit.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/push-tokens")
@RequiredArgsConstructor
@Tag(name = "v1 Push Token", description = "모바일 서버 push 디바이스 토큰 API")
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
public class PushDeviceTokenV1Controller {

    private final PushDeviceTokenService pushDeviceTokenService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "push token 등록", description = "로그인 사용자의 모바일 push token을 등록하거나 갱신합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PushDeviceTokenResponse>> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PushDeviceTokenRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        PushDeviceTokenResponse response = pushDeviceTokenService.registerForOwner(request, owner);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "push token 조회", description = "로그인 사용자의 활성 push token 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PushDeviceTokenResponse>>> getActiveTokens(@AuthenticationPrincipal Jwt jwt) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(pushDeviceTokenService.getActiveTokensForOwner(owner)));
    }

    @Operation(summary = "push token 해제", description = "로그인 사용자의 push token을 비활성화합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id
    ) {
        User owner = currentUserService.requireUser(jwt);
        pushDeviceTokenService.deactivateForOwner(id, owner);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
