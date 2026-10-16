package com.verduleria.gestion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/** Ejecutar únicamente contra una base PostgreSQL de pruebas. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class DatabaseMigrationTest {
    @Test
    void migrationIsRepeatableAndConstraintsProtectOrders() throws Exception {
        String url = System.getenv("TEST_DB_URL");
        String user = System.getenv("TEST_DB_USER");
        String password = System.getenv("TEST_DB_PASSWORD");
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            try {
                Flyway flyway = Flyway.configure().dataSource(url, user, password)
                        .schemas(schema).defaultSchema(schema).load();
                assertEquals(2, flyway.migrate().migrationsExecuted);
                assertEquals(0, flyway.migrate().migrationsExecuted);
                try (var sql = connection.createStatement()) {
                    sql.execute("SET search_path TO " + schema);
                    try (var result = sql.executeQuery("SELECT id FROM admin_bootstrap_guard")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                    sql.execute("INSERT INTO admin_user (username, password_hash) VALUES ('owner', 'test-hash-only')");
                    SQLException duplicateAdmin = assertThrows(SQLException.class, () -> sql.execute(
                            "INSERT INTO admin_user (username, password_hash) VALUES ('OWNER', 'test-hash-only')"));
                    assertEquals("23505", duplicateAdmin.getSQLState());
                    sql.execute("INSERT INTO customer (phone_e164) VALUES ('+5493511234567')");
                    SQLException duplicate = assertThrows(SQLException.class, () ->
                            sql.execute("INSERT INTO customer (phone_e164) VALUES ('+5493511234567')"));
                    assertEquals("23505", duplicate.getSQLState());
                    SQLException invalidOrder = assertThrows(SQLException.class, () -> sql.execute(
                            "INSERT INTO customer_order (customer_id, source, status) "
                            + "SELECT id, 'MANUAL', 'CONFIRMED' FROM customer LIMIT 1"));
                    assertEquals("23514", invalidOrder.getSQLState());
                    sql.execute("INSERT INTO customer_order (customer_id, source) "
                            + "SELECT id, 'MANUAL' FROM customer LIMIT 1");
                    try (var result = sql.executeQuery("SELECT total FROM customer_order")) {
                        assertTrue(result.next());
                        assertNull(result.getBigDecimal(1));
                    }
                    sql.execute("INSERT INTO webhook_event (deduplication_key, payload) VALUES ('message-1', '{}')");
                    SQLException duplicateEvent = assertThrows(SQLException.class, () -> sql.execute(
                            "INSERT INTO webhook_event (deduplication_key, payload) VALUES ('message-1', '{}')"));
                    assertEquals("23505", duplicateEvent.getSQLState());
                }
            } finally {
                try (var sql = connection.createStatement()) {
                    sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}
