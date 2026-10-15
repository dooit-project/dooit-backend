package pj.dooit.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

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
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("API 오류 응답 한글 payload는 UTF-8로 유지한다")
    void doFilterInternal_keepsKoreanErrorPayloadAsUtf8(CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"message\":\"서버 오류가 발생했습니다.\"}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("서버 오류가 발생했습니다.");
        assertThat(output).contains("서버 오류가 발생했습니다.");
        assertThat(output).doesNotContain("ìë²");
    }

    @Test
    @DisplayName("전문 로깅은 request/response 민감 값을 마스킹한다")
    void doFilterInternal_masksSensitivePayload(CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tasks");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Authorization", "Bearer raw-token");
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"data\":{\"accessToken\":\"issued-token\"}}");
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("API_REQUEST_IN", "API_RESPONSE_OUT");
        assertThat(output).contains("  method    : POST", "  path      : /api/v1/tasks");
        assertThat(output).contains("  requestBody  : ", "  responseBody : ");
        assertThat(output).contains("[MASKED]");
        assertThat(output).doesNotContain("user@example.com", "plain-password", "raw-token", "issued-token");
    }

    @Test
    @DisplayName("인증 API는 전문 로깅을 켜도 body를 남기지 않는다")
    void doFilterInternal_excludesAuthPayload(CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"plain-password\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"data\":{\"accessToken\":\"issued-token\"}}");
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("[PAYLOAD_PATH_EXCLUDED]");
        assertThat(output).doesNotContain("user@example.com", "plain-password", "issued-token");
    }

    @Test
    @DisplayName("헤더는 과하게 마스킹하지 않고 검색어 query parameter는 마스킹한다")
    void doFilterInternal_masksQueryWithoutOverMaskingHeaders(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tasks/search");
        request.setQueryString("q=private-search&date=2026-07-28");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("Authorization", "Bearer raw-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            servletResponse.getWriter().write("{\"success\":true}");
        };

        filter.doFilter(request, response, chain);

        assertThat(output).contains("q=[MASKED]&date=2026-07-28");
        assertThat(output).contains("Content-Type=application/json");
        assertThat(output).contains("Authorization=[MASKED]");
        assertThat(output).doesNotContain("private-search", "Content-Type=[MASKED]");
    }

    @Test
    @DisplayName("API가 아닌 요청은 필터 대상에서 제외한다")
    void shouldNotFilter_skipsNonApiPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tasks/today");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/goals", "/api/v1/goals/draft", "/api/v1/goals/1/plan-draft",
            "/api/v1/goals/1/adjustment-draft", "/api/v1/goals/1/journey-summary",
            "/api/v1/goals/1/journey", "/api/v1/goal-plans/1",
            "/api/v1/goal-plan-occurrences/1/result", "/api/v1/goal-direct-logs/1",
            "/api/v1/personal-systems/1/evidence", "/api/v1/personal-system-occurrences/1/result"
    })
    @DisplayName("계획 API는 설정 변경과 context path에도 개인 본문과 query를 기록하거나 캐시하지 않는다")
    void excludesPrivatePlanningEvenWithCustomConfiguration(String path, CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        properties.setPayloadExcludedPaths(Set.of());
        properties.setSensitivePayloadFields(Set.of());
        properties.setSensitiveQueryParameters(Set.of());
        properties.setSensitiveHeaders(Set.of());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/backend" + path);
        request.setContextPath("/backend");
        request.addHeader("Authorization", "private-bearer-token");
        request.addHeader("Cookie", "private-cookie");
        request.addHeader("X-API-Key", "private-api-key");
        request.setQueryString("motivation=private-query&unknown=private-unknown-query");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String input = "{\"motivation\":\"private-motivation\",\"difficulties\":[\"private-difficulty\"],\"unknown\":\"private-input\"}";
        request.setContent(input.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String result = "{\"nextAction\":\"private-next-action\",\"unknown\":\"private-output\"}";

        filter.doFilter(request, response, (req, res) -> {
            assertThat(req).isNotInstanceOf(ContentCachingRequestWrapper.class);
            assertThat(res).isNotInstanceOf(ContentCachingResponseWrapper.class);
            assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(input);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(400);
            ((jakarta.servlet.http.HttpServletResponse) res).addHeader("Set-Cookie", "session=private-response-cookie");
            res.getWriter().write(result);
        });

        assertThat(output).contains("API_REQUEST_IN", "API_RESPONSE_OUT", "[PAYLOAD_PATH_EXCLUDED]",
                "[QUERY_PATH_EXCLUDED]", "status       : 400");
        assertThat(output).doesNotContain("private-");
        assertThat(response.getContentAsString()).isEqualTo(result);
        assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void similarPathStillUsesConfiguredPayloadLogging(CapturedOutput output) throws Exception {
        properties.setPayloadEnabled(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/goals-report");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.getWriter().write("{\"success\":true}");
        });
        assertThat(output).contains("{\"success\":true}").doesNotContain("[PAYLOAD_PATH_EXCLUDED]");
    }
}
