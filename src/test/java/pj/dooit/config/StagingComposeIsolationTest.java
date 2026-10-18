package pj.dooit.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StagingComposeIsolationTest {
    @Test
    void stagingConfigCannotInheritProductionDatabaseCredentialsOrVolumes() throws Exception {
        ProcessBuilder command = new ProcessBuilder("docker", "compose", "--env-file", "/dev/null",
                "-p", "stg_dooit", "-f", "docker-compose.stg.yml", "config", "--format", "json")
                .directory(Path.of(".").toFile()).redirectErrorStream(true);
        command.environment().put("COMPOSE_DISABLE_ENV_FILE", "1");
        command.environment().put("STG_MYSQL_ROOT_PASSWORD", "test-stg-root");
        command.environment().put("STG_DB_PASSWORD", "test-stg-password");
        command.environment().put("STG_JWT_SECRET", "test-stg-secret");
        command.environment().put("STG_APP_IMAGE_TAG", "stg-test");
        command.environment().put("DOOIT_DB_PASSWORD", "must-not-inherit-production");
        command.environment().put("DOOIT_DB_URL", "jdbc:mysql://production:3306/production");
        command.environment().put("DOOIT_JWT_SECRET", "must-not-inherit-production");
        Process process = command.start();
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).isTrue();
        String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.exitValue()).isZero();
        JsonNode config = new ObjectMapper().readTree(output);
        JsonNode services = config.get("services");
        JsonNode app = services.get("app");
        assertThat(config.get("name").asString()).isEqualTo("stg_dooit");
        assertThat(app.get("environment").get("DOOIT_DB_URL").asString()).isEqualTo("jdbc:mysql://mysql:3306/dooit_stg");
        assertThat(app.get("environment").get("DOOIT_DB_PASSWORD").asString()).isEqualTo("test-stg-password");
        assertThat(app.get("environment").get("DOOIT_JWT_SECRET").asString()).isEqualTo("test-stg-secret");
        assertThat(app.get("environment").get("DOOIT_PUSH_ENABLED").asString()).isEqualTo("false");
        assertThat(app.get("environment").get("APP_BATCH_SCHEDULER_ENABLED").asString()).isEqualTo("false");
        assertThat(app.get("ports").get(0).get("host_ip").asString()).isEqualTo("127.0.0.1");
        assertThat(app.get("ports").get(0).get("published").asString()).isEqualTo("18080");
        assertThat(services.get("mysql").has("ports")).isFalse();
        assertThat(config.get("volumes").get("mysql-data").get("name").asString()).isEqualTo("stg_dooit_mysql-data");
        assertThat(output).doesNotContain("must-not-inherit-production", "dooit-mysql-data", "dooit-app-logs");
    }
}
