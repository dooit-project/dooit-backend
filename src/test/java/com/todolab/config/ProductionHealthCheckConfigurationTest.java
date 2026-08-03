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
        assertThat(compose).contains("APP_BATCH_SCHEDULER_ENABLED: ${APP_BATCH_SCHEDULER_ENABLED:-false}");
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
    }

    @Test
    @DisplayName("로컬 production runbook은 release, Tailscale, 백업, 복구 절차를 안내한다")
    void localProductionRunbookDocumentsOperations() throws Exception {
        String runbook = Files.readString(Path.of("docs/ops/LOCAL_PRODUCTION_RUNBOOK.md"));
        String docsIndex = Files.readString(Path.of("docs/README.md"));

        assertThat(docsIndex).contains("ops/LOCAL_PRODUCTION_RUNBOOK.md");
        assertThat(runbook).contains("docker compose up -d --build");
        assertThat(runbook).contains("tailscale serve --bg http://127.0.0.1:8080");
        assertThat(runbook).contains("./scripts/backup-db.sh");
        assertThat(runbook).contains("TODOLAB_CONFIRM_RESTORE=RESTORE ./scripts/restore-db.sh");
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
        assertThat(script).contains("https://*.ts.net");
        assertThat(script).doesNotContain("printf 'TODOLAB_JWT_SECRET=%s\\n' \"$jwt_secret\"");
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
        assertThat(runbook).contains("이미 같은 table, column, index, constraint가 적용된 DB에는 다시 실행하지 않는다.");
    }
}
