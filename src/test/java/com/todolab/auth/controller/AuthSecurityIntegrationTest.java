package com.todolab.auth.controller;

import com.todolab.auth.security.ApiAccessDeniedHandler;
import com.todolab.auth.service.JwtTokenService;
import com.todolab.common.api.ErrorCode;
import com.todolab.mail.MailService;
import com.todolab.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("내 인증 정보 조회 성공 - 유효한 Bearer 토큰이면 사용자 클레임을 반환한다")
    void me_success() throws Exception {
        User user = new User("test@example.com", "encoded-password", "테스터");
        ReflectionTestUtils.setField(user, "id", 7L);
        String accessToken = jwtTokenService.createAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.accountType").value("REGISTERED"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("테스터"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("내 인증 정보 조회 성공 - 게스트 Bearer 토큰이면 GUEST 계정 정보를 반환한다")
    void me_guestSuccess() throws Exception {
        User user = User.guest(java.time.LocalDateTime.of(2026, 9, 9, 0, 0));
        ReflectionTestUtils.setField(user, "id", 8L);
        String accessToken = jwtTokenService.createGuestAccessToken(user).tokenValue();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.id").value(8))
                .andExpect(jsonPath("$.data.accountType").value("GUEST"))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.role").value("USER"));
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
