package com.todolab.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationIndexTest {

    private static final List<DocumentLink> FRONTEND_DOCS = List.of(
            new DocumentLink("docs/api/API_V1_FRONTEND.md", "api/API_V1_FRONTEND.md"),
            new DocumentLink("docs/api/GUEST_ACCOUNT_HANDOFF.md", "api/GUEST_ACCOUNT_HANDOFF.md"),
            new DocumentLink("docs/ops/ENVIRONMENT_INTEGRATION.md", "ops/ENVIRONMENT_INTEGRATION.md"),
            new DocumentLink("docs/api/AUTH_CONTRACT.md", "api/AUTH_CONTRACT.md"),
            new DocumentLink("docs/api/API_ERROR_CODES.md", "api/API_ERROR_CODES.md"),
            new DocumentLink("docs/api/DATA_MODEL_GLOSSARY.md", "api/DATA_MODEL_GLOSSARY.md"),
            new DocumentLink("docs/api/TIMEZONE_CONTRACT.md", "api/TIMEZONE_CONTRACT.md"),
            new DocumentLink("docs/api/RECURRENCE_MODEL.md", "api/RECURRENCE_MODEL.md"),
            new DocumentLink("docs/api/NOTIFICATION_CONTRACT.md", "api/NOTIFICATION_CONTRACT.md"),
            new DocumentLink("docs/api/SHARING_CONTRACT.md", "api/SHARING_CONTRACT.md")
    );

    @Test
    @DisplayName("README와 docs index는 프론트/모바일 전달 문서를 안내한다")
    void readmeAndDocsIndexLinkFrontendDocuments() throws Exception {
        String rootReadme = Files.readString(Path.of("README.md"));
        String docsReadme = Files.readString(Path.of("docs/README.md"));

        assertThat(docsReadme).contains("프론트/모바일 전달 문서");
        assertThat(rootReadme).contains("문서 전체 목록과 전달 우선순위");
        FRONTEND_DOCS.forEach(doc -> {
            assertThat(rootReadme).contains(doc.rootPath());
            assertThat(docsReadme).contains(doc.docsPath());
        });
    }

    @Test
    @DisplayName("문서 인덱스는 유지 원칙과 핵심 계약 문서를 안내한다")
    void docsIndexDefinesMaintenanceRules() throws Exception {
        String docsIndex = Files.readString(Path.of("docs/README.md"));
        String auth = Files.readString(Path.of("docs/api/AUTH_CONTRACT.md"));
        String guest = Files.readString(Path.of("docs/api/GUEST_ACCOUNT_HANDOFF.md"));

        assertThat(docsIndex).contains("## 유지 원칙");
        assertThat(docsIndex).contains("## 현재 기준 요약");
        assertThat(docsIndex).contains("아직 닫히지 않은 제품/운영 작업");
        assertThat(docsIndex).contains("실제 도메인 DNS/TLS 연결 후 public production smoke");
        assertThat(docsIndex).contains("완료 이력만 남은 문서는 별도로 유지하지 않고");
        assertThat(docsIndex).doesNotContain("history/TASK_DATE_MIGRATION.md");
        assertThat(docsIndex).doesNotContain("BACKEND_DOCUMENTATION_PLAN.md");
        assertThat(auth).contains("POST /api/v1/auth/guest");
        assertThat(auth).contains("게스트 access token TTL은 31일");
        assertThat(auth).contains("`accountType` claim은 `GUEST`");
        assertThat(auth).contains("같은 user id를 유지하고 `REGISTERED`로 승격한다");
        assertThat(auth).contains("token claim이 불일치하므로 401로 거부한다");
        assertThat(auth).contains("게스트 병합 정책");
        assertThat(auth).contains("push device token, push notification history");
        assertThat(auth).contains("멱등 성공으로 처리한다");
        assertThat(auth).contains("게스트 만료 정리 정책");
        assertThat(auth).contains("TODOLAB_GUEST_CLEANUP_ENABLED");
        assertThat(auth).contains("게스트 생성 Rate Limit");
        assertThat(auth).contains("GUEST_CREATION_RATE_LIMIT_EXCEEDED(11004)");
        assertThat(auth).contains("다중 서버에서 공유 window");
        assertThat(auth).contains("비밀번호 재설정");
        assertThat(auth).contains("PASSWORD_RESET_TOKEN");
        assertThat(auth).contains("TODOLAB_PASSWORD_RESET_LINK_TEMPLATE");
        assertThat(auth).contains("POST /api/v1/auth/refresh");
        assertThat(auth).contains("REFRESH_TOKEN_SESSION");
        assertThat(auth).contains("refresh token 재사용 감지");
        assertThat(auth).contains("POST /api/v1/auth/logout");
        assertThat(guest).contains("기존 계정 로그인 및 게스트 병합");
        assertThat(guest).contains("backend commit은 이 문서가 포함된 최신 `main` 커밋을 사용한다.");
    }

    @Test
    @DisplayName("로드맵은 앞으로 닫아야 할 제품 기능과 production 작업을 관리한다")
    void roadmapDocumentsNextBacklog() throws Exception {
        String roadmap = Files.readString(Path.of("docs/project/ROADMAP.md"));

        assertThat(roadmap).contains("## 2. 제품 기능 로드맵");
        assertThat(roadmap).contains("### 2026-08-27 정리");
        assertThat(roadmap).contains("현재 남은 P0는 기능 구현보다 production 접근성과 운영 복구성 검증");
        assertThat(roadmap).contains("서버 push 실제 발송");
        assertThat(roadmap).contains("### P0. 프론트 출시 연동 요청");
        assertThat(roadmap).contains("비밀번호 재설정 request/verify/confirm API");
        assertThat(roadmap).contains("### P1. 프론트 계약 후속 요청");
        assertThat(roadmap).contains("Task별 알림 preference");
        assertThat(roadmap).contains("### P0. 일정 빠른 등록 API");
        assertThat(roadmap).contains("### P0. 빠른 등록 템플릿");
        assertThat(roadmap).contains("### P1. 일정 공유 설계");
        assertThat(roadmap).contains("### P1. 일정 공유 1차 구현");
        assertThat(roadmap).contains("### P1. 서버 push 실제 발송");
        assertThat(roadmap).contains("### P1. 검색/추천 고도화");
        assertThat(roadmap).contains("검색 결과에 `matchedFields`와 `highlight`를 반환한다");
        assertThat(roadmap).contains("반복 여부 필터와 반복 매칭 ranking을 추가한다");
        assertThat(roadmap).contains("모바일에서 빈 검색 결과일 때 추천 query/category를 반환한다");
        assertThat(roadmap).contains("### P2. 장기 게스트 복구");
        assertThat(roadmap).contains("POST /api/v1/tasks/quick-capture");
        assertThat(roadmap).contains("SHARED_WORKSPACE");
        assertThat(roadmap).contains("## 3. 운영 마무리 로드맵");
        assertThat(roadmap).contains("### P0. production 접근 경로 확정");
        assertThat(roadmap).contains("### P0. Android production smoke");
        assertThat(roadmap).contains("### P0. host 상시 가용성 검증");
        assertThat(roadmap).contains("### P1. offsite backup 확정");
        assertThat(roadmap).contains("### P2. 제품 정책 후속 결정");
        assertThat(roadmap).contains("TODOLAB_REQUIRE_TAILSCALE_URL=true");
        assertThat(roadmap).contains("완료 이력은 로드맵에 길게 누적하지 않고");
    }

    @Test
    @DisplayName("공유 설계 문서는 개인 owner scope 보존과 workspace API 분리를 안내한다")
    void sharingContractDocumentsOwnerScopeInvariant() throws Exception {
        String sharing = Files.readString(Path.of("docs/api/SHARING_CONTRACT.md"));

        assertThat(sharing).contains("기존 `/api/v1/tasks/**`, `/api/v1/dday-goals/**` 개인 API에는 공유 데이터가 섞이지 않아야 한다.");
        assertThat(sharing).contains("`PERSONAL`");
        assertThat(sharing).contains("`WORKSPACE`");
        assertThat(sharing).contains("SHARED_WORKSPACE");
        assertThat(sharing).contains("WORKSPACE_MEMBER");
        assertThat(sharing).contains("게스트 계정은 초대 대상과 workspace 생성 대상에서 제외한다.");
        assertThat(sharing).contains("개인 Task API는 workspace Task를 반환하지 않는다.");
        assertThat(sharing).contains("Task 템플릿은 1차 정책에서 personal-only 리소스로 유지한다.");
        assertThat(sharing).contains("workspace 템플릿 공유와 개인 템플릿 기반 workspace Task 생성은 제공하지 않는다.");
        assertThat(sharing).contains("## Workspace 삭제 정책");
        assertThat(sharing).contains("하위 `TASK`, `DDAY_GOAL`, `RECURRENCE_SERIES`, `WORKSPACE_MEMBER`");
    }

    @Test
    @DisplayName("반복 모델 문서는 timezone과 rule 변경 시 occurrence 보존 정책을 안내한다")
    void recurrenceModelDocumentsTimeZonePolicy() throws Exception {
        String recurrence = Files.readString(Path.of("docs/api/RECURRENCE_MODEL.md"));

        assertThat(recurrence).contains("사용자 timezone 변경은 기존 `RecurrenceSeries.timeZone`");
        assertThat(recurrence).contains("TIMEZONE_CONTRACT.md");
        assertThat(recurrence).contains("## 반복 Rule 변경 정책");
        assertThat(recurrence).contains("PUT /api/v1/tasks/{id}/recurrence-rule");
        assertThat(recurrence).contains("`effectiveDate` 이전 완료 occurrence");
        assertThat(recurrence).contains("새 rule 범위 밖이 된 미래 일반 occurrence는 삭제 대상");
        assertThat(recurrence).contains("DELETE /api/v1/tasks/{occurrenceId}?recurrenceScope=THIS");
        assertThat(recurrence).contains("별도 `skip` endpoint는 제공하지 않는다");
    }

    @Test
    @DisplayName("알림 계약과 운영 문서는 서버 push provider 설정을 안내한다")
    void notificationDocsDocumentPushProvider() throws Exception {
        String notification = Files.readString(Path.of("docs/api/NOTIFICATION_CONTRACT.md"));
        String environment = Files.readString(Path.of("docs/ops/ENVIRONMENT_INTEGRATION.md"));

        assertThat(notification).contains("1차 provider는 `EXPO`");
        assertThat(notification).contains("## 현재 구현 상태");
        assertThat(notification).contains("Expo Push Service 단건 발송 client");
        assertThat(notification).contains("scheduler 기반 자동 발송");
        assertThat(notification).contains("TODOLAB_PUSH_ENABLED");
        assertThat(notification).contains("TODOLAB_PUSH_ACCESS_TOKEN");
        assertThat(notification).contains("TODOLAB_PUSH_SCHEDULER_FIXED_DELAY");
        assertThat(notification).contains("TODOLAB_PUSH_LOOK_AHEAD_WINDOW");
        assertThat(notification).contains("EAS/Expo project에 보관");
        assertThat(notification).contains("서버 Push 발송 스케줄러 설계");
        assertThat(notification).contains("idempotency key는 `SERVER:{task.id}`");
        assertThat(notification).contains("`SUCCESS` 이력이 있으면 다시 발송하지 않는다");
        assertThat(notification).contains("`FAILED` 이력만 있는 key는 다음 scheduler cycle의 재시도 대상");
        assertThat(notification).contains("전송 실패 Token 비활성화 정책");
        assertThat(notification).contains("DeviceNotRegistered");
        assertThat(notification).contains("credential 오류와 payload 오류는 token 자체 문제로 보지 않고 token을 유지한다");
        assertThat(notification).contains("suppressLocalNotification=true");
        assertThat(notification).contains("GET /api/v1/push-notification-histories");
        assertThat(notification).contains("PUSH_NOTIFICATION_HISTORY");
        assertThat(environment).contains("TODOLAB_PUSH_PROVIDER=EXPO");
        assertThat(environment).contains("TODOLAB_PUSH_ACCESS_TOKEN=");
        assertThat(environment).contains("TODOLAB_PUSH_SCHEDULER_FIXED_DELAY=PT1M");
        assertThat(environment).contains("TODOLAB_PUSH_LOOK_AHEAD_WINDOW=PT10M");
        assertThat(environment).contains("TODOLAB_SPRINGDOC_SWAGGER_UI_ENABLED");
        assertThat(environment).contains("local 프로필은 프론트 연동 디버깅을 위해 `payload-enabled=true`");
        assertThat(environment).contains("## 5. Production Health Check");
        assertThat(environment).contains("/actuator/health/readiness");
        assertThat(environment).contains("`schema`: 운영에 필요한 핵심 table 존재 여부");
    }

    @Test
    @DisplayName("운영 환경 문서는 staging/production 확정 입력값을 안내한다")
    void environmentDocsDocumentDeploymentInputs() throws Exception {
        String environment = Files.readString(Path.of("docs/ops/ENVIRONMENT_INTEGRATION.md"));

        assertThat(environment).contains("## 4. 운영 환경 확정 입력값");
        assertThat(environment).contains("## 현재 운영 입력 상태");
        assertThat(environment).contains("실제 도메인을 구매해 연결하면");
        assertThat(environment).contains("API base URL");
        assertThat(environment).contains("Web origin");
        assertThat(environment).contains("Authorization`, `Content-Type`, `Idempotency-Key` header 포함 preflight 성공");
        assertThat(environment).contains("Native 앱은 CORS 대상이 아니므로 API URL 접근성과 인증 흐름만 확인한다.");
        assertThat(environment).contains("Cache-Control: no-store");
        assertThat(environment).contains("./scripts/check-public-production.sh");
    }

    private record DocumentLink(String rootPath, String docsPath) {
    }
}
