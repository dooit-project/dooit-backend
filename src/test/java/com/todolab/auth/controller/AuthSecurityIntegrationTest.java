package com.todolab.auth.controller;

import com.todolab.auth.security.ApiAccessDeniedHandler;
import com.todolab.auth.dto.RegisterRequest;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.common.api.ErrorCode;
import com.todolab.mail.MailService;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenService jwtTokenService;

    @Autowired
    ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("내 인증 정보 조회 성공 - 유효한 Bearer 토큰이면 사용자 클레임을 반환한다")
    void me_success() throws Exception {
        User user = userRepository.save(new User("test@example.com", "encoded-password", "테스터"));
        String accessToken = jwtTokenService.createAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("테스터"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("내 인증 정보 조회 성공 - 게스트 Bearer 토큰이면 GUEST 계정 정보를 반환한다")
    void me_guestSuccess() throws Exception {
        User user = userRepository.save(User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0)));
        String accessToken = jwtTokenService.createGuestAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.accountType").value("GUEST"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("게스트 token으로 회원가입하면 같은 user id가 정식 계정으로 승격되고 기존 guest token은 무효화된다")
    void register_promotesGuestAndInvalidatesGuestToken() throws Exception {
        String guestToken = mockMvc.perform(post("/api/v1/auth/guest"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.accountType").value("GUEST"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long guestUserId = objectMapper.readTree(guestToken).get("data").get("user").get("id").longValue();
        String guestAccessToken = objectMapper.readTree(guestToken).get("data").get("accessToken").asText();

        RegisterRequest request = new RegisterRequest("guest-promote@example.com", "password123", "승격 사용자");
        String registeredToken = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + guestAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.user.id").value(guestUserId))
                .andExpect(jsonPath("$.data.user.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.user.email").value("guest-promote@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String registeredAccessToken = objectMapper.readTree(registeredToken).get("data").get("accessToken").asText();

        assertThat(userRepository.findById(guestUserId)).get()
                .satisfies(user -> {
                    assertThat(user.getAccountType().name()).isEqualTo("REGISTERED");
                    assertThat(user.getEmail()).isEqualTo("guest-promote@example.com");
                    assertThat(user.getGuestExpiresAt()).isNull();
                });

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + guestAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + registeredAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(guestUserId))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"));
    }

    @Test
    @DisplayName("내 인증 정보 조회 실패 - 토큰이 없으면 401을 반환한다")
    void me_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("fail"))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    @DisplayName("API 접근 권한이 부족하면 403 오류 envelope를 반환한다")
    void apiForbiddenEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        apiAccessDeniedHandler.handle(
                new MockHttpServletRequest("GET", "/api/v1/tasks"),
                response,
                new AccessDeniedException("denied")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":11003");
        assertThat(response.getContentAsString()).contains("접근 권한이 없습니다.");
    }
}
