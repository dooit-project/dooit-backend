package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneContractDocumentationTest {

    @Test
    @DisplayName("timezone 계약 문서는 현재 기준과 향후 도입 조건을 설명한다")
    void timezoneContractDocumentsCurrentAndFuturePolicy() throws Exception {
        String content = Files.readString(Path.of("docs/api/TIMEZONE_CONTRACT.md"));

        assertThat(content).contains("Asia/Seoul");
        assertThat(content).contains("Constant.ZONE");
        assertThat(content).contains("offset 없는 `LocalDateTime`");
        assertThat(content).contains("사용자별 timezone을 도입하려면");
        assertThat(content).contains("User profile에 IANA timezone 필드 추가");
    }
}
