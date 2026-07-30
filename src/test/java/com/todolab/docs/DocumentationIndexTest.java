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
            new DocumentLink("docs/ops/ENVIRONMENT_INTEGRATION.md", "ops/ENVIRONMENT_INTEGRATION.md"),
            new DocumentLink("docs/api/AUTH_CONTRACT.md", "api/AUTH_CONTRACT.md"),
            new DocumentLink("docs/api/API_ERROR_CODES.md", "api/API_ERROR_CODES.md"),
            new DocumentLink("docs/api/DATA_MODEL_GLOSSARY.md", "api/DATA_MODEL_GLOSSARY.md"),
            new DocumentLink("docs/api/TIMEZONE_CONTRACT.md", "api/TIMEZONE_CONTRACT.md"),
            new DocumentLink("docs/api/RECURRENCE_MODEL.md", "api/RECURRENCE_MODEL.md"),
            new DocumentLink("docs/api/NOTIFICATION_CONTRACT.md", "api/NOTIFICATION_CONTRACT.md")
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
    @DisplayName("문서 유지 계획은 문서 그룹과 커밋 전 점검 기준을 안내한다")
    void documentationPlanDefinesMaintenanceRules() throws Exception {
        String plan = Files.readString(Path.of("docs/project/BACKEND_DOCUMENTATION_PLAN.md"));

        assertThat(plan).contains("## 문서 그룹");
        assertThat(plan).contains("## 유지 원칙");
        assertThat(plan).contains("## 커밋 전 점검");
    }

    @Test
    @DisplayName("로드맵은 닫힌 기존 범위 이후의 다음 백로그를 관리한다")
    void roadmapDocumentsNextBacklog() throws Exception {
        String roadmap = Files.readString(Path.of("docs/project/ROADMAP.md"));

        assertThat(roadmap).contains("## 6. 다음 백로그");
        assertThat(roadmap).contains("사용자 timezone 날짜 경계 적용");
        assertThat(roadmap).contains("서버 push 발송");
        assertThat(roadmap).contains("반복 rule 수정");
        assertThat(roadmap).contains("운영 환경 확정");
    }

    @Test
    @DisplayName("반복 모델 문서는 timezone 변경 시 occurrence 보존 정책을 안내한다")
    void recurrenceModelDocumentsTimeZonePolicy() throws Exception {
        String recurrence = Files.readString(Path.of("docs/api/RECURRENCE_MODEL.md"));

        assertThat(recurrence).contains("사용자 timezone 변경은 기존 `RecurrenceSeries.timeZone`");
        assertThat(recurrence).contains("TIMEZONE_CONTRACT.md");
    }

    @Test
    @DisplayName("알림 계약과 운영 문서는 서버 push provider 설정을 안내한다")
    void notificationDocsDocumentPushProvider() throws Exception {
        String notification = Files.readString(Path.of("docs/api/NOTIFICATION_CONTRACT.md"));
        String environment = Files.readString(Path.of("docs/ops/ENVIRONMENT_INTEGRATION.md"));

        assertThat(notification).contains("1차 provider는 `EXPO`");
        assertThat(notification).contains("TODOLAB_PUSH_ENABLED");
        assertThat(notification).contains("서버 Push 발송 스케줄러 설계");
        assertThat(notification).contains("idempotency key는 `SERVER:{task.id}`");
        assertThat(notification).contains("전송 실패 Token 비활성화 정책");
        assertThat(notification).contains("DeviceNotRegistered");
        assertThat(notification).contains("suppressLocalNotification=true");
        assertThat(notification).contains("GET /api/v1/push-notification-histories");
        assertThat(notification).contains("PUSH_NOTIFICATION_HISTORY");
        assertThat(environment).contains("TODOLAB_PUSH_PROVIDER=EXPO");
    }

    private record DocumentLink(String rootPath, String docsPath) {
    }
}
