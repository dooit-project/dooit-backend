package com.todolab.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component("schema")
public class SchemaHealthIndicator implements HealthIndicator {

    private static final List<String> REQUIRED_TABLES = List.of(
            "APP_USER",
            "DDAY_GOAL",
            "PASSWORD_RESET_TOKEN",
            "PUSH_DEVICE_TOKEN",
            "PUSH_NOTIFICATION_HISTORY",
            "RECURRENCE_SERIES",
            "TASK"
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
            List<String> missingTables = REQUIRED_TABLES.stream()
                    .filter(table -> !tableExists(metadata, table))
                    .toList();

            if (!missingTables.isEmpty()) {
                return Health.down()
                        .withDetail("missingTables", missingTables)
                        .build();
            }

            return Health.up()
                    .withDetail("checkedTables", REQUIRED_TABLES.size())
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }

    private boolean tableExists(DatabaseMetaData metadata, String tableName) {
        List<String> candidates = new ArrayList<>();
        candidates.add(tableName);
        candidates.add(tableName.toLowerCase());

        for (String candidate : candidates) {
            if (tableExistsWithName(metadata, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean tableExistsWithName(DatabaseMetaData metadata, String tableName) {
        try (ResultSet tables = metadata.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return tables.next();
        } catch (Exception e) {
            return false;
        }
    }
}
