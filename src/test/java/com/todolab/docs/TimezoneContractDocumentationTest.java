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
        assertThat(content).contains("사용자 profile에는 IANA timezone ID를 저장할 수 있으며 기본값은 `Asia/Seoul`이다.");
        assertThat(content).contains("PATCH /api/v1/users/me/time-zone");
        assertThat(content).contains("Today, Calendar, 알림 후보 조회의 일정 overlap 경계");
        assertThat(content).contains("사용자 timezone별 조회 경계는 제공하지만, timezone 변경은 기존 반복 series/occurrence를 자동 재계산하지 않는다.");
        assertThat(content).contains("이미 materialize된 occurrence Task의 `startAt`, `endAt`, `targetDate`, `occurrenceDate`는 자동 변경하지 않는다.");
    }

    @Test
    @DisplayName("모바일 상태 문서는 timezone 변경 정책 확정 상태를 추적한다")
    void mobileStatusTracksTimeZoneChangePolicy() throws Exception {
        String content = Files.readString(Path.of("docs/mobile/MOBILE_API_BACKEND_STATUS.md"));

        assertThat(content).contains("[x] timezone 변경 시 기존 반복 occurrence 재계산/보존 정책 확정");
        assertThat(content).contains("사용자 timezone 변경은 조회 경계에만 영향을 주며, 기존 반복 series/occurrence는 자동 재계산하지 않는다.");
        assertThat(content).contains("docs/api/TIMEZONE_CONTRACT.md");
    }
}
