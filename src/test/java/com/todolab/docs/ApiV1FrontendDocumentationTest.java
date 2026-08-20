package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApiV1FrontendDocumentationTest {

    private static final Path API_DOC = Path.of("docs/api/API_V1_FRONTEND.md");

    @Test
    @DisplayName("v1 연동 문서는 공통 계약 원본 문서를 연결한다")
    void apiDocLinksCommonContractDocuments() throws Exception {
        String content = Files.readString(API_DOC);

        assertThat(content).contains("ENVIRONMENT_INTEGRATION.md");
        assertThat(content).contains("API_ERROR_CODES.md");
        assertThat(content).contains("AUTH_CONTRACT.md");
        assertThat(content).contains("401은 토큰 없음/만료/위변조");
        assertThat(content).contains("403은 인증 후 권한 부족");
        assertThat(content).contains("PATCH /api/v1/users/me/time-zone");
        assertThat(content).contains("Today/Calendar/알림 후보 조회의 일정 overlap 날짜 경계에 적용");
        assertThat(content).contains("POST /api/v1/auth/guest");
        assertThat(content).contains("accountType: 'GUEST' | 'REGISTERED'");
        assertThat(content).contains("게스트 상태 회원가입 승격은 지원한다.");
        assertThat(content).contains("기존 계정 로그인 병합은 지원한다.");
        assertThat(content).contains("만료 게스트와 관련 owner 데이터 정리는 운영 스케줄러로 지원한다.");
        assertThat(content).contains("게스트 생성 rate limit은 기본 클라이언트 신호별 30건/1시간");
        assertThat(content).contains("Authorization: Bearer <guest-access-token>");
        assertThat(content).contains("기존 게스트 user id를 유지한 채 `REGISTERED`로 승격한다.");
        assertThat(content).contains("기존 guest token은 DB의 현재 `accountType`과 token claim이 불일치하므로 owner API와 `/auth/me`에서 401 처리된다.");
        assertThat(content).contains("게스트 데이터 병합 로그인");
        assertThat(content).contains("Task와 일정, Today 순서, 완료 상태, 미룸 사유");
        assertThat(content).contains("같은 guest token으로 같은 target 계정 로그인을 재시도하면 중복 이전 없이 정식 token을 다시 반환한다.");
    }

    @Test
    @DisplayName("v1 연동 문서는 검색과 Today 일괄 재정렬 계약을 포함한다")
    void apiDocIncludesSearchAndTodayOrderContracts() throws Exception {
        String content = Files.readString(API_DOC);

        assertThat(content).contains("GET /api/v1/tasks/search");
        assertThat(content).contains("제목/설명/category/D-Day 제목 부분 검색어");
        assertThat(content).contains("matchedFields");
        assertThat(content).contains("DDAY_GOAL_TITLE");
        assertThat(content).contains("같은 sort 안에서 제목, category, D-Day 제목, 설명 매칭 순서");
        assertThat(content).contains("PUT /api/v1/tasks/today-order");
        assertThat(content).contains("GET /api/v1/tasks/notification-candidates");
        assertThat(content).contains("suppressLocalNotification");
        assertThat(content).contains("POST /api/v1/push-tokens");
        assertThat(content).contains("DELETE /api/v1/push-tokens/{id}");
        assertThat(content).contains("GET /api/v1/push-notification-histories");
        assertThat(content).contains("PushNotificationHistoryResponse");
        assertThat(content).doesNotContain("- 알림 전송 이력 API");
        assertThat(content).contains("반복 생성 UI는 `POST /api/v1/tasks`의 `recurrence` 하위 객체 사용");
        assertThat(content).contains("PUT /api/v1/tasks/{id}/recurrence-rule");
        assertThat(content).contains("반복 기존 rule 수정 UI는 `PUT /api/v1/tasks/{id}/recurrence-rule`로 분리");
        assertThat(content).contains("DELETE /api/v1/tasks/{occurrenceId}?recurrenceScope=THIS");
        assertThat(content).contains("별도 `skip` endpoint 없이");
        assertThat(content).contains("현재 cursor는 마지막으로 받은 항목의 `task.id` 문자열이다.");
        assertThat(content).contains("Today drag-and-drop 저장은 `PUT /api/v1/tasks/today-order` 사용");
    }
}
