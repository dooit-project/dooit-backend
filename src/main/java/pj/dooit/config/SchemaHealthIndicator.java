package pj.dooit.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.sql.SQLFeatureNotSupportedException;

@Component("schema")
public class SchemaHealthIndicator implements HealthIndicator {

    private static final List<String> REQUIRED_TABLES = List.of(
            "APP_USER",
            "CALENDAR_FEED_TOKEN",
            "DDAY_GOAL",
            "IDEMPOTENCY_RECORD",
            "PASSWORD_RESET_TOKEN",
            "PUSH_DEVICE_TOKEN",
            "PUSH_NOTIFICATION_HISTORY",
            "REFRESH_TOKEN_SESSION",
            "RECURRENCE_SERIES",
            "TASK",
            "GOAL",
            "GOAL_VERSION",
            "GOAL_PLAN",
            "GOAL_PLAN_VERSION",
            "GOAL_PLAN_OCCURRENCE",
            "GOAL_PRACTICE_RESULT",
            "GOAL_DIRECT_LOG",
            "PERSONAL_SYSTEM",
            "PERSONAL_SYSTEM_VERSION",
            "PERSONAL_SYSTEM_OCCURRENCE",
            "PERSONAL_SYSTEM_PRACTICE_RESULT"
    );
    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.of(
            "TASK", Set.of("LINKED_GOAL_ID"),
            "GOAL_DIRECT_LOG", Set.of("CREATE_REQUEST_HASH"),
            "PERSONAL_SYSTEM", Set.of("MIGRATED_GOAL_ID", "MIGRATED_PLAN_ID"),
            "PERSONAL_SYSTEM_OCCURRENCE", Set.of("MIGRATED_OCCURRENCE_ID")
    );

    private final ObjectProvider<DataSource> dataSourceProvider;

    public SchemaHealthIndicator(ObjectProvider<DataSource> dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    @Override
    public Health health() {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return Health.down()
                    .withDetail("reason", "DataSource bean is not available")
                    .build();
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = currentSchema(connection);
            if (catalog == null && schema == null) {
                return Health.down().withDetail("reason", "Current database namespace is unavailable").build();
            }
            Set<String> tables = new HashSet<>();
            try (ResultSet rows = metadata.getTables(catalog, schema, null, new String[]{"TABLE"})) {
                while (rows.next()) {
                    if (inNamespace(rows, catalog, schema)) tables.add(rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT));
                }
            }
            List<String> missingTables = REQUIRED_TABLES.stream()
                    .filter(table -> !tables.contains(table))
                    .toList();
            Map<String, Set<String>> columns = new HashMap<>();
            try (ResultSet rows = metadata.getColumns(catalog, schema, null, null)) {
                while (rows.next()) {
                    if (!inNamespace(rows, catalog, schema)) continue;
                    String table = rows.getString("TABLE_NAME").toUpperCase(Locale.ROOT);
                    if (tables.contains(table) && REQUIRED_COLUMNS.containsKey(table)) {
                        columns.computeIfAbsent(table, ignored -> new HashSet<>())
                                .add(rows.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                    }
                }
            }
            List<String> missingColumns = REQUIRED_COLUMNS.entrySet().stream()
                    .filter(entry -> tables.contains(entry.getKey()))
                    .flatMap(entry -> entry.getValue().stream()
                            .filter(column -> !columns.getOrDefault(entry.getKey(), Set.of()).contains(column))
                            .map(column -> entry.getKey() + "." + column))
                    .sorted().toList();
            if (!missingTables.isEmpty() || !missingColumns.isEmpty()) {
                return Health.down()
                        .withDetail("missingTables", missingTables)
                        .withDetail("missingColumns", missingColumns)
                        .build();
            }

            return Health.up()
                    .withDetail("checkedTables", REQUIRED_TABLES.size())
                    .withDetail("checkedColumns", REQUIRED_COLUMNS.values().stream().mapToInt(Set::size).sum())
                    .build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", "Schema metadata check failed").build();
        }
    }

    private String currentSchema(Connection connection) throws java.sql.SQLException {
        try { return connection.getSchema(); }
        catch (SQLFeatureNotSupportedException ignored) { return null; }
    }

    // JDBC schema arguments are patterns: verify returned namespaces exactly as well.
    private boolean inNamespace(ResultSet row, String catalog, String schema) throws java.sql.SQLException {
        return (catalog == null || catalog.equals(row.getString("TABLE_CAT")))
                && (schema == null || schema.equals(row.getString("TABLE_SCHEM")));
    }
}
