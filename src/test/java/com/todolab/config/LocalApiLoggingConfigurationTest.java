package com.todolab.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalApiLoggingConfigurationTest {

    @Test
    @DisplayName("local 설정은 API 요청/응답 전문을 마스킹 없이 기록한다")
    void localApiLoggingPayloadEnabledWithoutMasking() throws Exception {
        String content = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(content).contains("active: local");
        assertThat(content).contains("payload-enabled: true");
        assertThat(content).contains("max-payload-length: 65536");
        assertThat(content).contains("sensitive-headers: []");
        assertThat(content).contains("sensitive-query-parameters: []");
        assertThat(content).contains("sensitive-payload-fields: []");
        assertThat(content).contains("payload-excluded-paths: []");
    }
}
