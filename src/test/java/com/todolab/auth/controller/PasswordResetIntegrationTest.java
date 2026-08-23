package com.todolab.auth.controller;

import com.todolab.auth.dto.LoginRequest;
import com.todolab.auth.repository.PasswordResetTokenRepository;
import com.todolab.mail.MailService;
import com.todolab.user.domain.User;
import com.todolab.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "app.auth.password-reset.max-requests-per-window=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordResetIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockitoBean
    MailService mailService;

    @BeforeEach
    void setUp() {
        passwordResetTokenRepository.deleteAll();
        userRepository.deleteAll();
        reset(mailService);
    }

    @Test
    @DisplayName("비밀번호 재설정 request, verify, confirm 후 새 비밀번호로 로그인한다")
    void passwordReset_success() throws Exception {
        userRepository.save(new User("reset@example.com", passwordEncoder.encode("old-password"), "사용자"));

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"RESET@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(true))
                .andExpect(jsonPath("$.data.ttlSeconds").value(1800));

        String token = extractTokenFromMail("reset@example.com");

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.maskedEmail").value("r***@example.com"));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("reset@example.com", "old-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(11001));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("reset@example.com", "new-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("reset@example.com"));

        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(11005));
    }

    @Test
    @DisplayName("없는 이메일의 비밀번호 재설정 요청도 200을 반환하고 메일을 보내지 않는다")
    void passwordReset_unknownEmailDoesNotRevealAccountExistence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requested").value(true));

        verifyNoInteractions(mailService);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 email 기준 rate limit을 적용한다")
    void passwordReset_rateLimit() throws Exception {
        userRepository.save(new User("limit@example.com", passwordEncoder.encode("old-password"), "사용자"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"limit@example.com\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"limit@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value(11006));
    }

    @Test
    @DisplayName("잘못된 비밀번호 재설정 token은 400을 반환한다")
    void passwordReset_invalidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(11005));

        verify(mailService, never()).sendText(anyString(), anyString(), anyString());
    }

    private String extractTokenFromMail(String email) {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendText(eq(email), anyString(), bodyCaptor.capture());
        Matcher matcher = TOKEN_PATTERN.matcher(bodyCaptor.getValue());
        if (!matcher.find()) {
            throw new AssertionError("reset token not found in mail body");
        }
        return matcher.group(1);
    }
}
