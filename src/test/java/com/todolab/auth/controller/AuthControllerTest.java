package com.todolab.auth.controller;

import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.dto.TokenResponse;
import com.todolab.auth.exception.GuestCreationRateLimitExceededException;
import com.todolab.auth.exception.InvalidCredentialsException;
import com.todolab.auth.service.AuthService;
import com.todolab.auth.service.CurrentUserService;
import com.todolab.auth.service.GuestAccountRateLimiter;
import com.todolab.common.api.ApiExceptionHandler;
import com.todolab.common.api.ErrorCode;
import com.todolab.user.domain.AccountType;
import com.todolab.user.domain.UserRole;
import com.todolab.user.dto.UserResponse;
import com.todolab.user.exception.UserEmailAlreadyExistsException;
import com.todolab.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(ApiExceptionHandler.class)
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserService userService;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    CurrentUserService currentUserService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    GuestAccountRateLimiter guestAccountRateLimiter;

    @Test
    @DisplayName("회원가입 성공")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "테스터");
        UserResponse response = new UserResponse(
                1L,
                "test@example.com",
                "테스터",
                UserRole.USER,
                LocalDateTime.of(2026, 6, 30, 0, 30),
                null
        );

        given(userService.register(any(RegisterRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("테스터"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        then(userService).should().register(any(RegisterRequest.class));
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("회원가입 실패 - 요청값이 올바르지 않으면 400을 반환한다")
    void register_invalidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("not-email", "short", "");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT.getCode()));

        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("회원가입 실패 - 이미 가입된 이메일이면 409를 반환한다")
    void register_duplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "테스터");
        given(userService.register(any(RegisterRequest.class)))
                .willThrow(new UserEmailAlreadyExistsException("test@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.USER_EMAIL_ALREADY_EXISTS.getCode()));

        then(userService).should().register(any(RegisterRequest.class));
        then(userService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("게스트 회원가입 승격 성공 - 같은 사용자 id의 정식 token을 반환한다")
    void register_promoteGuest() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "테스터");
        UserResponse user = new UserResponse(
                10L,
                "test@example.com",
                "테스터",
                UserRole.USER,
                LocalDateTime.of(2026, 8, 9, 0, 0),
                null
        );
        TokenResponse response = new TokenResponse(
                "Bearer",
                "registered-token",
                LocalDateTime.of(2026, 8, 9, 1, 0),
                user
        );
        given(authService.promoteGuest(any(AuthService.JwtTokenPrincipal.class), any(RegisterRequest.class)))
                .willReturn(response);
        given(jwtDecoder.decode("guest-token")).willReturn(Jwt.withTokenValue("guest-token")
                .header("alg", "HS256")
                .subject("10")
                .claim("accountType", "GUEST")
                .build());

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("registered-token"))
                .andExpect(jsonPath("$.data.user.id").value(10))
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));

        then(authService).should().promoteGuest(any(AuthService.JwtTokenPrincipal.class), any(RegisterRequest.class));
        then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("로그인 성공 - Bearer access token을 반환한다")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        UserResponse user = new UserResponse(
                1L,
                "test@example.com",
                "테스터",
                UserRole.USER,
                LocalDateTime.of(2026, 6, 30, 0, 30),
                null
        );
        TokenResponse response = new TokenResponse(
                "Bearer",
                "access-token",
                LocalDateTime.of(2026, 6, 30, 1, 30),
                user
        );

        given(authService.login(any(), any(LoginRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.user.email").value("test@example.com"));

        then(authService).should().login(any(), any(LoginRequest.class));
        then(authService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("로그인 실패 - 인증 정보가 올바르지 않으면 401을 반환한다")
    void login_invalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "wrong-password");
        given(authService.login(any(), any(LoginRequest.class))).willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_CREDENTIALS.getCode()));

        then(authService).should().login(any(), any(LoginRequest.class));
        then(authService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("게스트 계정 생성 성공 - Bearer access token과 GUEST 사용자를 반환한다")
    void guest_success() throws Exception {
        UserResponse user = new UserResponse(
                10L,
                AccountType.GUEST,
                null,
                null,
                UserRole.USER,
                "Asia/Seoul",
                LocalDateTime.of(2026, 8, 9, 0, 0),
                null
        );
        TokenResponse response = new TokenResponse(
                "Bearer",
                "guest-token",
                LocalDateTime.of(2026, 9, 9, 0, 0),
                user
        );

        given(authService.createGuest()).willReturn(response);

        mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").value("guest-token"))
                .andExpect(jsonPath("$.data.user.id").value(10))
                .andExpect(jsonPath("$.data.user.accountType").value("GUEST"))
                .andExpect(jsonPath("$.data.user.email").doesNotExist())
                .andExpect(jsonPath("$.data.user.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        then(authService).should().createGuest();
        then(guestAccountRateLimiter).should().assertGuestCreationAllowed(any());
    }

    @Test
    @DisplayName("게스트 계정 생성 실패 - rate limit 초과 시 429를 반환한다")
    void guest_rateLimitExceeded() throws Exception {
        willThrow(new GuestCreationRateLimitExceededException())
                .given(guestAccountRateLimiter)
                .assertGuestCreationAllowed(any());

        mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.GUEST_CREATION_RATE_LIMIT_EXCEEDED.getCode()));

        then(authService).shouldHaveNoInteractions();
    }
}
