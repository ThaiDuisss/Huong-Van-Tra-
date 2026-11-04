package org.hvtra.orderservice.returnrefund;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** No URL, username or password is read from application configuration or the environment. */
final class IsolatedMySql {
    static final String DATABASE = "order_return_refund_integration_test";
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.7")
            .withDatabaseName(DATABASE).withUsername("return_refund_it")
            .withPassword(UUID.randomUUID().toString())
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withCommand("--innodb-lock-wait-timeout=10")
            .withStartupTimeout(Duration.ofMinutes(3));

    static synchronized void start() {
        if (!MYSQL.isRunning()) MYSQL.start();
    }

    static void verify(Connection connection) throws Exception {
        if (!MYSQL.isRunning() || !MYSQL.getJdbcUrl().equals(connection.getMetaData().getURL())) {
            throw new IllegalStateException("BLOCKED: datasource is not the owned MySQL container");
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("containerId", MYSQL.getContainerId());
        evidence.put("image", MYSQL.getDockerImageName());
        evidence.put("jdbcUrl", connection.getMetaData().getURL());
        try (var statement = connection.createStatement()) {
            try (ResultSet rows = statement.executeQuery("SELECT DATABASE(), CURRENT_USER(), VERSION(), @@transaction_isolation")) {
                rows.next();
                if (!DATABASE.equals(rows.getString(1)) || !rows.getString(2).startsWith("return_refund_it@")) {
                    throw new IllegalStateException("BLOCKED: unexpected database or account");
                }
                evidence.put("database", rows.getString(1));
                evidence.put("user", rows.getString(2));
                evidence.put("mysqlVersion", rows.getString(3));
                evidence.put("isolation", rows.getString(4));
            }
            List<String> grants = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
                while (rows.next()) {
                    String grant = rows.getString(1);
                    if (!grant.contains("GRANT USAGE ON *.*")
                            && !grant.contains("ON `" + DATABASE.replace("_", "\\_") + "`.* TO")) {
                        evidence("rejected-grant", Map.of("grant", grant));
                        throw new IllegalStateException("BLOCKED: test account has privileges outside test schema");
                    }
                    grants.add(grant);
                }
            }
            if (grants.size() < 2) throw new IllegalStateException("BLOCKED: test grants not verified");
            evidence.put("grants", grants);
        }
        evidence("datasource-isolation", evidence);
    }

    /** Root is used only inside this owned container to observe lock waits, never to run business writes. */
    static long lockWaits() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM performance_schema.data_lock_waits")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    static synchronized void evidence(String name, Object value) {
        try {
            Path folder = Path.of("target", "return-refund-evidence");
            Files.createDirectories(folder);
            new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                    .writeValue(folder.resolve(name + ".json").toFile(), value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not save integration evidence", exception);
        }
    }
    private IsolatedMySql() { }
}
