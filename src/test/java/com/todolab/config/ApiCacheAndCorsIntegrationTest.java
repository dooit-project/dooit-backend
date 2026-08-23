package com.todolab.config;

import com.todolab.mail.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "app.cors.allowed-origins=https://web.example.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiCacheAndCorsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MailService mailService;

    @Test
    @DisplayName("API 성공 응답은 Cache-Control no-store를 반환한다")
    void apiSuccessResponse_hasNoStoreCacheControl() throws Exception {
        mockMvc.perform(get("/api/v1/system/metadata"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"));
    }

    @Test
    @DisplayName("API 인증 실패 응답도 JSON과 Cache-Control no-store를 반환한다")
    void apiUnauthorizedResponse_hasNoStoreCacheControl() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"));
    }

    @Test
    @DisplayName("운영 Web CORS preflight는 Authorization, Content-Type, Idempotency-Key를 허용한다")
    void corsPreflight_allowsFrontendHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://web.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://web.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Idempotency-Key")));
    }

    @Test
    @DisplayName("CORS 실제 API 응답은 Idempotency-Replayed header 노출을 선언한다")
    void corsActualResponse_exposesIdempotencyReplayedHeader() throws Exception {
        mockMvc.perform(get("/api/v1/system/metadata")
                        .header(HttpHeaders.ORIGIN, "https://web.example.com"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://web.example.com"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(CorsConfig.IDEMPOTENCY_REPLAYED_HEADER)
                ));
    }
}
