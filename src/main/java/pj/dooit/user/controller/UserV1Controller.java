package pj.dooit.user.controller;

import pj.dooit.auth.service.CurrentUserService;
import pj.dooit.common.api.ApiResponse;
import pj.dooit.user.domain.User;
import pj.dooit.user.dto.UserResponse;
import pj.dooit.user.dto.UserTimeZoneRequest;
import pj.dooit.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "v1 User", description = "모바일 사용자 설정 API")
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
public class UserV1Controller {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "내 timezone 변경", description = "로그인 사용자의 IANA timezone 설정을 저장합니다.")
    @PatchMapping("/me/time-zone")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyTimeZone(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserTimeZoneRequest request
    ) {
        User owner = currentUserService.requireUser(jwt);
        return ResponseEntity.ok(ApiResponse.success(userService.updateTimeZoneForOwner(owner, request)));
    }
}
