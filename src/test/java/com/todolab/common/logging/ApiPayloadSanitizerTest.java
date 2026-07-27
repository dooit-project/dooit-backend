package com.todolab.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiPayloadSanitizerTest {

    private final ApiPayloadSanitizer sanitizer = new ApiPayloadSanitizer(new ObjectMapper());

    @Test
    @DisplayName("JSON 전문의 민감 필드를 재귀적으로 마스킹한다")
    void sanitizeJsonPayload_masksSensitiveFields() {
        String payload = """
                {
                  "email": "user@example.com",
                  "password": "plain-password",
                  "profile": {
                    "accessToken": "access-token",
                    "items": [
                      {"refresh_token": "refresh-token"}
                    ]
                  }
                }
                """;

        String result = sanitizer.sanitizeJsonPayload(payload, Set.of("password", "token"), 4096);

        assertThat(result).contains("\"email\":\"user@example.com\"");
        assertThat(result).contains("\"password\":\"[MASKED]\"");
        assertThat(result).contains("\"accessToken\":\"[MASKED]\"");
        assertThat(result).contains("\"refresh_token\":\"[MASKED]\"");
        assertThat(result).doesNotContain("plain-password", "access-token", "refresh-token");
    }

    @Test
    @DisplayName("전문 길이를 제한하고 초과 여부를 표시한다")
    void sanitizeJsonPayload_truncatesPayload() {
        String result = sanitizer.sanitizeJsonPayload("{\"title\":\"abcdef\"}", Set.of(), 10);

        assertThat(result).isEqualTo("{\"title\":\"...[TRUNCATED]");
    }

    @Test
    @DisplayName("로그 값을 한 줄로 정규화해 로그 삽입을 방지한다")
    void sanitizeValue_normalizesControlCharacters() {
        String result = sanitizer.sanitizeValue("user-agent", "app\r\nforged\tlog", Set.of(), 4096);

        assertThat(result).isEqualTo("app\\r\\nforged\\tlog");
    }

    @Test
    @DisplayName("정확 매칭 마스킹은 content-type 같은 운영 헤더를 보존한다")
    void sanitizeExactValue_masksOnlyExactKey() {
        assertThat(sanitizer.sanitizeExactValue("Authorization", "Bearer token", Set.of("authorization"), 4096))
                .isEqualTo("[MASKED]");
        assertThat(sanitizer.sanitizeExactValue("Content-Type", "application/json", Set.of("content"), 4096))
                .isEqualTo("application/json");
    }
}
