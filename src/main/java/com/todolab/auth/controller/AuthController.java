package com.todolab.auth.controller;

import com.todolab.auth.dto.AuthenticatedUserResponse;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.common.api.ApiResponse;
import com.todolab.auth.service.AuthService;
import com.todolab.user.dto.UserResponse;
import com.todolab.user.service.UserService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "v1 Auth", description = "모바일 JWT 인증 API")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청값 검증 실패",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증 실패 또는 인증 필요",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 가입된 이메일",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "서버 오류",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
        )
})
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 표시 이름으로 모바일 API 사용자를 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "게스트 계정 생성", description = "로그인 없이 사용할 수 있는 서버 발급형 게스트 계정과 Bearer access token을 생성합니다.")
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<TokenResponse>> guest() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.createGuest()));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 Bearer access token을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @Operation(
            summary = "내 인증 정보 조회",
            description = "Bearer access token의 사용자 클레임을 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthenticatedUserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUserResponse response = new AuthenticatedUserResponse(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("accountType") == null
                        ? com.todolab.user.domain.AccountType.REGISTERED
                        : com.todolab.user.domain.AccountType.valueOf(jwt.getClaimAsString("accountType")),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("displayName"),
                jwt.getClaimAsString("role")
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
