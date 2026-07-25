package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApiV1FrontendDocumentationTest {

    private static final Path API_DOC = Path.of("docs/API_V1_FRONTEND.md");

    @Test
    @DisplayName("v1 연동 문서는 공통 계약 원본 문서를 연결한다")
    void apiDocLinksCommonContractDocuments() throws Exception {
        String content = Files.readString(API_DOC);

        assertThat(content).contains("ENVIRONMENT_INTEGRATION.md");
        assertThat(content).contains("API_ERROR_CODES.md");
        assertThat(content).contains("AUTH_CONTRACT.md");
        assertThat(content).contains("401은 토큰 없음/만료/위변조");
        assertThat(content).contains("403은 인증 후 권한 부족");
    }

    @Test
    @DisplayName("v1 연동 문서는 검색과 Today 일괄 재정렬 계약을 포함한다")
    void apiDocIncludesSearchAndTodayOrderContracts() throws Exception {
        String content = Files.readString(API_DOC);

        assertThat(content).contains("GET /api/v1/tasks/search");
        assertThat(content).contains("PUT /api/v1/tasks/today-order");
        assertThat(content).contains("현재 cursor는 마지막으로 받은 항목의 `task.id` 문자열이다.");
        assertThat(content).contains("Today drag-and-drop 저장은 `PUT /api/v1/tasks/today-order` 사용");
    }
}
