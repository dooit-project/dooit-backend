package com.todolab.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionHealthCheckConfigurationTest {

    @Test
    @DisplayName("Docker image와 Compose는 readiness endpoint로 app health check를 수행한다")
    void dockerHealthCheckUsesReadinessEndpoint() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(dockerfile).contains("HEALTHCHECK");
        assertThat(dockerfile).contains("curl -fsS http://127.0.0.1:8080/actuator/health/readiness");
        assertThat(compose).contains("healthcheck:");
        assertThat(compose).contains("http://127.0.0.1:8080/actuator/health/readiness");
    }

    @Test
    @DisplayName("Compose production 기본값은 DB port를 공개하지 않고 app만 loopback에 바인딩한다")
    void composeBindsOnlyApplicationToLoopback() throws Exception {
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(compose).doesNotContain("\"3306:3306\"");
        assertThat(compose).contains("\"127.0.0.1:8080:8080\"");
        assertThat(compose).contains("TODOLAB_JWT_SECRET");
        assertThat(compose).contains("TODOLAB_GUEST_JWT_ACCESS_TOKEN_TTL: ${TODOLAB_GUEST_JWT_ACCESS_TOKEN_TTL:-P31D}");
        assertThat(compose).contains("TODOLAB_PASSWORD_RESET_TOKEN_TTL: ${TODOLAB_PASSWORD_RESET_TOKEN_TTL:-PT30M}");
        assertThat(compose).contains("TODOLAB_PASSWORD_RESET_LINK_TEMPLATE:");
        assertThat(compose).contains("APP_BATCH_SCHEDULER_ENABLED: ${APP_BATCH_SCHEDULER_ENABLED:-false}");
        assertThat(Files.readString(Path.of(".env.example"))).contains("TODOLAB_GUEST_JWT_ACCESS_TOKEN_TTL=P31D");
    }

    @Test
    @DisplayName("Compose와 Dockerfile은 app image tag와 Docker log rotation을 설정한다")
    void composeAndDockerfileConfigureImageTagAndLogRotation() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String compose = Files.readString(Path.of("docker-compose.yml"));

        assertThat(dockerfile).contains("ARG APP_VERSION=local");
        assertThat(dockerfile).contains("org.opencontainers.image.revision");
        assertThat(compose).contains("image: todolab-backend:${TODOLAB_APP_IMAGE_TAG:-local}");
        assertThat(compose).contains("APP_VERSION: ${TODOLAB_APP_IMAGE_TAG:-local}");
        assertThat(compose).contains("TODOLAB_APP_COMMIT_SHA: ${TODOLAB_APP_COMMIT_SHA:-local}");
        assertThat(compose).contains("TODOLAB_APP_IMAGE_TAG: ${TODOLAB_APP_IMAGE_TAG:-local}");
        assertThat(compose).contains("driver: json-file");
        assertThat(compose).contains("max-size: \"10m\"");
        assertThat(compose).contains("max-file: \"5\"");
    }

    @Test
    @DisplayName("Actuator health 설정은 readiness에 DB와 schema indicator를 포함한다")
    void actuatorReadinessIncludesDatabaseAndSchema() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(application).contains("include: health");
        assertThat(application).contains("show-components: always");
        assertThat(application).contains("validate-group-membership: false");
        assertThat(application).contains("readiness:");
        assertThat(application).contains("include: readinessState,db,schema");
        assertThat(Files.readString(Path.of("src/main/java/com/todolab/config/SchemaHealthIndicator.java")))
                .contains("PASSWORD_RESET_TOKEN")
                .contains("IDEMPOTENCY_RECORD");
    }

    @Test
    @DisplayName("로컬 production runbook은 release, Tailscale, 백업, 복구 절차를 안내한다")
    void localProductionRunbookDocumentsOperations() throws Exception {
        String runbook = Files.readString(Path.of("docs/ops/LOCAL_PRODUCTION_RUNBOOK.md"));
        String docsIndex = Files.readString(Path.of("docs/README.md"));

        assertThat(docsIndex).contains("ops/LOCAL_PRODUCTION_RUNBOOK.md");
        assertThat(runbook).contains("docker compose up -d --build");
        assertThat(runbook).contains("./scripts/check-production-env.sh");
        assertThat(runbook).contains("tailscale serve --bg http://127.0.0.1:8080");
        assertThat(runbook).contains("./scripts/backup-db.sh");
        assertThat(runbook).contains("TODOLAB_CONFIRM_RESTORE=RESTORE ./scripts/restore-db.sh");
        assertThat(runbook).contains("./scripts/release-production.sh");
        assertThat(runbook).contains("./scripts/rollback-production.sh <previous-image-tag>");
        assertThat(runbook).contains("max-size=10m");
    }

    @Test
    @DisplayName("production 환경 점검 스크립트는 secret 출력 없이 필수 설정을 검증한다")
    void productionEnvironmentCheckScriptValidatesConfigurationWithoutPrintingSecrets() throws Exception {
        String script = Files.readString(Path.of("scripts/check-production-env.sh"));
        String routine = Files.readString(Path.of("scripts/check-production-routine.sh"));
        String report = Files.readString(Path.of("scripts/report-production-status.sh"));
        String envExample = Files.readString(Path.of(".env.example"));

        assertThat(script).contains("TODOLAB_JWT_SECRET must be at least 32 bytes");
        assertThat(script).contains("jwtSecretBytes=valid");
        assertThat(script).contains("TODOLAB_JWT_ISSUER \"$jwt_issuer\"");
        assertThat(script).contains("guestJwtTtl=");
        assertThat(script).contains("TODOLAB_REQUIRE_TAILSCALE_URL");
        assertThat(script).contains("TODOLAB_REQUIRE_PUBLIC_API_URL");
        assertThat(script).contains("TODOLAB_REQUIRE_OFFSITE_BACKUP");
        assertThat(script).contains("publicApiUrl=");
        assertThat(script).doesNotContain("echo \"$jwt_secret\"");
        assertThat(routine).contains("./scripts/check-production-env.sh");
        assertThat(report).contains("backendCommit=");
        assertThat(report).contains("guestRefreshApi=supported");
        assertThat(report).contains("mergeResultCounts=supported");
        assertThat(report).doesNotContain("TODOLAB_JWT_SECRET");
        assertThat(envExample).contains("TODOLAB_TAILSCALE_API_URL=");
        assertThat(envExample).contains("TODOLAB_PUBLIC_API_URL=");
        assertThat(envExample).contains("TODOLAB_TAILSCALE_CLI=tailscale");
        assertThat(envExample).contains("TODOLAB_OFFSITE_BACKUP_DIR=");
    }

    @Test
    @DisplayName("Tailscale production 점검은 macOS Tailscale.app CLI 경로를 fallback으로 사용한다")
    void tailscaleProductionCheckFallsBackToMacosAppCli() throws Exception {
        String script = Files.readString(Path.of("scripts/check-tailscale-production.sh"));
        String runbook = Files.readString(Path.of("docs/ops/LOCAL_PRODUCTION_RUNBOOK.md"));

        assertThat(script).contains("/Applications/Tailscale.app/Contents/MacOS/Tailscale");
        assertThat(script).contains("command -v tailscale");
        assertThat(script).contains("tailscale_cli=$default_macos_tailscale_cli");
        assertThat(script).contains("grep -Eo 'https://[^[:space:]]+' \"$serve_status\"");
        assertThat(runbook).contains("점검 스크립트는 `/Applications/Tailscale.app/Contents/MacOS/Tailscale`을 자동 사용한다");
    }

    @Test
    @DisplayName("DB 백업과 복구 스크립트는 압축 검증과 복구 확인 플래그를 둔다")
    void databaseBackupAndRestoreScriptsHaveSafetyChecks() throws Exception {
        String backup = Files.readString(Path.of("scripts/backup-db.sh"));
        String restore = Files.readString(Path.of("scripts/restore-db.sh"));

        assertThat(backup).contains("set -euo pipefail");
        assertThat(backup).contains("mysqldump --single-transaction");
        assertThat(backup).contains("gzip -t \"$backup_file\"");
        assertThat(backup).contains("TODOLAB_BACKUP_RETENTION_DAYS:-14");
        assertThat(restore).contains("set -euo pipefail");
        assertThat(restore).contains("TODOLAB_CONFIRM_RESTORE");
        assertThat(restore).contains("docker compose stop app");
        assertThat(restore).contains("gzip -dc \"$backup_file\"");
    }

    @Test
    @DisplayName("production 환경 초기화 스크립트는 JWT secret을 생성하고 기존 값을 덮어쓰지 않는다")
    void productionEnvironmentScriptGeneratesJwtSecretWithoutOverwritingExistingValues() throws Exception {
        String script = Files.readString(Path.of("scripts/configure-production-env.sh"));

        assertThat(script).contains("openssl rand -base64 48");
        assertThat(script).contains("set_env_value \"TODOLAB_JWT_ISSUER\"");
        assertThat(script).contains("set_env_value \"TODOLAB_JWT_SECRET\"");
        assertThat(script).contains("already has a non-placeholder value; refusing to overwrite .env");
        assertThat(script).contains("set_env_value \"TODOLAB_PUBLIC_API_URL\"");
        assertThat(script).contains("validate_https_url");
        assertThat(script).doesNotContain("printf 'TODOLAB_JWT_SECRET=%s\\n' \"$jwt_secret\"");
    }

    @Test
    @DisplayName("public production 점검 스크립트는 실제 도메인 HTTPS API 계약을 확인한다")
    void publicProductionCheckValidatesHttpsApiContract() throws Exception {
        String script = Files.readString(Path.of("scripts/check-public-production.sh"));
        String environment = Files.readString(Path.of("docs/ops/ENVIRONMENT_INTEGRATION.md"));

        assertThat(script).contains("TODOLAB_PUBLIC_API_URL");
        assertThat(script).contains("/actuator/health/readiness");
        assertThat(script).contains("/api/v1/system/metadata");
        assertThat(script).contains("/api/v1/auth/guest");
        assertThat(script).contains("/api/v1/auth/me");
        assertThat(script).contains("Access-Control-Request-Headers: Authorization,Content-Type,Idempotency-Key");
        assertThat(script).contains("Public production check passed.");
        assertThat(environment).contains("./scripts/check-public-production.sh");
    }

    @Test
    @DisplayName("로컬 production migration 문서는 기존 DB 수동 적용 절차를 안내한다")
    void localProductionMigrationDocumentsManualApplication() throws Exception {
        String migration = Files.readString(Path.of("docs/db/migrations/20260803_prepare_local_production.sql"));
        String runbook = Files.readString(Path.of("docs/ops/LOCAL_PRODUCTION_RUNBOOK.md"));

        assertThat(migration).contains("START TRANSACTION;");
        assertThat(migration).contains("CREATE TABLE APP_USER");
        assertThat(migration).contains("ALTER TABLE TASK");
        assertThat(migration).contains("CREATE TABLE PUSH_NOTIFICATION_HISTORY");
        assertThat(migration).contains("COMMIT;");
        assertThat(runbook).contains("docs/db/migrations/20260803_prepare_local_production.sql");
        assertThat(runbook).contains("docs/db/migrations/20260809_add_guest_account_columns.sql");
        assertThat(runbook).contains("이미 같은 table, column, index, constraint가 적용된 DB에는 다시 실행하지 않는다.");
    }

    @Test
    @DisplayName("release와 rollback 스크립트는 git tag image 배포와 이전 image 재기동을 지원한다")
    void releaseAndRollbackScriptsSupportTaggedImages() throws Exception {
        String release = Files.readString(Path.of("scripts/release-production.sh"));
        String rollback = Files.readString(Path.of("scripts/rollback-production.sh"));

        assertThat(release).contains("git rev-parse --short HEAD");
        assertThat(release).contains("git rev-parse HEAD");
        assertThat(release).contains("TODOLAB_APP_COMMIT_SHA=\"$commit_sha\"");
        assertThat(release).contains("TODOLAB_APP_IMAGE_TAG=\"$image_tag\" docker compose build app");
        assertThat(release).contains("./gradlew clean test bootJar");
        assertThat(release).contains("Rollback command: ./scripts/rollback-production.sh <previous-image-tag>");
        assertThat(rollback).contains("docker image inspect \"$image_name\"");
        assertThat(rollback).contains("TODOLAB_APP_COMMIT_SHA=\"$image_tag\"");
        assertThat(rollback).contains("TODOLAB_APP_IMAGE_TAG=\"$image_tag\" docker compose up -d --no-build app");
        assertThat(rollback).contains("/actuator/health/readiness");
    }

    @Test
    @DisplayName("백업 launchd 설치 스크립트는 매일 백업 LaunchAgent를 생성한다")
    void backupLaunchdInstallScriptCreatesDailyBackupAgent() throws Exception {
        String script = Files.readString(Path.of("scripts/install-backup-launchd.sh"));
        String runbook = Files.readString(Path.of("docs/ops/LOCAL_PRODUCTION_RUNBOOK.md"));

        assertThat(script).contains("com.todolab.backend.backup");
        assertThat(script).contains("StartCalendarInterval");
        assertThat(script).contains("TODOLAB_BACKUP_HOUR:-3");
        assertThat(script).contains("TODOLAB_BACKUP_MINUTE:-15");
        assertThat(script).contains("plutil -lint \"$plist_file\"");
        assertThat(script).contains("launchctl bootstrap");
        assertThat(runbook).contains("./scripts/install-backup-launchd.sh");
        assertThat(runbook).contains("launchctl print gui/$(id -u)/com.todolab.backend.backup");
    }
}
