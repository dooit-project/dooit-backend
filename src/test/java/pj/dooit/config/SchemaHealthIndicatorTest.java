package pj.dooit.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.health.contributor.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SchemaHealthIndicatorTest {
    private JdbcTemplate jdbc;
    private DataSource source;
    private SchemaHealthIndicator indicator;

    @BeforeEach void schema() {
        source = new DriverManagerDataSource("jdbc:h2:mem:schema-health-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        for (String table : List.of("APP_USER", "CALENDAR_FEED_TOKEN", "DDAY_GOAL", "IDEMPOTENCY_RECORD",
                "PASSWORD_RESET_TOKEN", "PUSH_DEVICE_TOKEN", "PUSH_NOTIFICATION_HISTORY", "REFRESH_TOKEN_SESSION",
                "RECURRENCE_SERIES", "TASK", "GOAL", "GOAL_VERSION", "GOAL_PLAN", "GOAL_PLAN_VERSION",
                "GOAL_PLAN_OCCURRENCE", "GOAL_PRACTICE_RESULT", "GOAL_DIRECT_LOG", "PERSONAL_SYSTEM",
                "PERSONAL_SYSTEM_VERSION", "PERSONAL_SYSTEM_OCCURRENCE", "PERSONAL_SYSTEM_PRACTICE_RESULT")) {
            jdbc.execute("CREATE TABLE " + table + " (ID BIGINT)");
        }
        jdbc.execute("ALTER TABLE TASK ADD LINKED_GOAL_ID BIGINT");
        jdbc.execute("ALTER TABLE GOAL_DIRECT_LOG ADD CREATE_REQUEST_HASH VARCHAR(64)");
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM ADD MIGRATED_GOAL_ID BIGINT");
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM ADD MIGRATED_PLAN_ID BIGINT");
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM_OCCURRENCE ADD MIGRATED_OCCURRENCE_ID BIGINT");
        indicator = indicator(source);
    }

    @AfterEach void cleanup() { jdbc.execute("DROP ALL OBJECTS"); }

    @Test void migratedSchemaIsReady() {
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("checkedTables", 21).containsEntry("checkedColumns", 5);
    }

    @Test void oldSchemaWithBaseTablesOnlyIsNotReady() {
        for (String table : List.of("GOAL", "GOAL_VERSION", "GOAL_PLAN", "GOAL_PLAN_VERSION", "GOAL_PLAN_OCCURRENCE",
                "GOAL_PRACTICE_RESULT", "GOAL_DIRECT_LOG", "PERSONAL_SYSTEM", "PERSONAL_SYSTEM_VERSION",
                "PERSONAL_SYSTEM_OCCURRENCE", "PERSONAL_SYSTEM_PRACTICE_RESULT")) jdbc.execute("DROP TABLE " + table);
        jdbc.execute("ALTER TABLE TASK DROP COLUMN LINKED_GOAL_ID");
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat((List<?>) health.getDetails().get("missingTables")).hasSize(11);
        assertThat(health.getDetails().get("missingColumns")).isEqualTo(List.of("TASK.LINKED_GOAL_ID"));
    }

    @ParameterizedTest
    @CsvSource({"TASK,LINKED_GOAL_ID", "GOAL_DIRECT_LOG,CREATE_REQUEST_HASH", "PERSONAL_SYSTEM,MIGRATED_GOAL_ID",
            "PERSONAL_SYSTEM,MIGRATED_PLAN_ID", "PERSONAL_SYSTEM_OCCURRENCE,MIGRATED_OCCURRENCE_ID"})
    void partialMigrationColumnIsNotReady(String table, String column) {
        jdbc.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("missingTables")).isEqualTo(List.of());
        assertThat(health.getDetails().get("missingColumns")).isEqualTo(List.of(table + "." + column));
    }

    @Test void wildcardLookalikeCannotSubstituteForRequiredTable() {
        jdbc.execute("ALTER TABLE PERSONAL_SYSTEM_VERSION RENAME TO PERSONALXSYSTEMXVERSION");
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("missingTables")).isEqualTo(List.of("PERSONAL_SYSTEM_VERSION"));
    }

    @Test void anotherSchemaTableCannotHideMissingCurrentSchemaTableOrColumn() {
        jdbc.execute("CREATE SCHEMA OTHER");
        jdbc.execute("CREATE TABLE OTHER.GOAL (ID BIGINT)");
        jdbc.execute("DROP TABLE GOAL");
        jdbc.execute("CREATE TABLE OTHER.GOAL_DIRECT_LOG (ID BIGINT, CREATE_REQUEST_HASH VARCHAR(64))");
        jdbc.execute("ALTER TABLE GOAL_DIRECT_LOG DROP COLUMN CREATE_REQUEST_HASH");
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("missingTables")).isEqualTo(List.of("GOAL"));
        assertThat(health.getDetails().get("missingColumns")).isEqualTo(List.of("GOAL_DIRECT_LOG.CREATE_REQUEST_HASH"));
    }

    @Test void metadataFailureDoesNotExposeConnectionDetails() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("private-user@private-host/private-database"));
        var health = indicator(source).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "Schema metadata check failed");
        assertThat(health.toString()).doesNotContain("private-");
    }

    @Test void schemaNameUnderscoreDoesNotMatchAnotherNamespace() throws Exception {
        jdbc.execute("CREATE SCHEMA APP_DATA");
        jdbc.execute("CREATE SCHEMA APPXDATA");
        jdbc.execute("CREATE TABLE APPXDATA.GOAL (ID BIGINT)");
        try (Connection connection = source.getConnection()) {
            connection.setSchema("APP_DATA");
            var health = indicator(new SingleConnectionDataSource(connection, true)).health();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails().get("missingTables")).asList().contains("GOAL");
        }
    }

    @Test void missingDataSourceAndUnknownDatabaseNamespaceFailClosed() throws Exception {
        assertThat(new SchemaHealthIndicator(new DefaultListableBeanFactory().getBeanProvider(DataSource.class))
                .health().getStatus()).isEqualTo(Status.DOWN);
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        assertThat(indicator(source).health().getStatus()).isEqualTo(Status.DOWN);
    }

    private SchemaHealthIndicator indicator(DataSource source) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("dataSource", source);
        return new SchemaHealthIndicator(beans.getBeanProvider(DataSource.class));
    }
}
