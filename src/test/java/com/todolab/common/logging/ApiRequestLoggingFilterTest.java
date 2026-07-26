package com.todolab.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ApiRequestLoggingFilterTest {

    private final ApiLoggingProperties properties = new ApiLoggingProperties();
    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(
            properties,
            new ApiPayloadSanitizer(new ObjectMapper())
    );

    @Test
    @DisplayName("API 요청에는 request id를 응답 헤더로 내려주고 response body를 유지한다")
    void doFilterInternal_addsRequestIdAndKeepsResponseBody() throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"title\":\"task\",\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"success\":true}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("전문 로깅은 request/response 민감 값을 마스킹한다")
    void doFilterInternal_masksSensitivePayload(CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Authorization", "Bearer raw-token");
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"data\":{\"accessToken\":\"issued-token\"}}");
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("[MASKED]");
        assertThat(output).doesNotContain("plain-password", "raw-token", "issued-token");
    }

    @Test
    @DisplayName("API가 아닌 요청은 필터 대상에서 제외한다")
    void shouldNotFilter_skipsNonApiPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tasks/today");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }
}
