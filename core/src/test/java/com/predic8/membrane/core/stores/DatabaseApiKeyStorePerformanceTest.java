package com.predic8.membrane.core.stores;

import com.predic8.membrane.core.interceptor.apikey.stores.DatabaseApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.KeyTable;
import com.predic8.membrane.core.interceptor.apikey.stores.ScopeTable;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatabaseApiKeyStorePerformanceTest {
    DatabaseApiKeyStore databaseApiKeyStore;
    EmbeddedDataSource dataSource;
    KeyTable keyTable;
    ScopeTable scopeTable;
    static final int USERS = 10;

    @BeforeEach
    void setUp() throws SQLException {
        databaseApiKeyStore = createDatabaseApiKeyStore();
        createTables();
    }

    private DatabaseApiKeyStore createDatabaseApiKeyStore() {
        DatabaseApiKeyStore apiKeyStore = new DatabaseApiKeyStore();
        keyTable = new KeyTable();
        scopeTable = new ScopeTable();
        keyTable.setName("key");
        scopeTable.setName("scope");
        apiKeyStore.setKeyTable(keyTable);
        apiKeyStore.setScopeTable(scopeTable);
        return apiKeyStore;
    }

    private Connection createConnection() throws SQLException {
        if (dataSource == null) {
            dataSource = new EmbeddedDataSource();
            dataSource.setDatabaseName("test");
            dataSource.setCreateDatabase("create");
            databaseApiKeyStore.setDatasource(dataSource);
        }
        return dataSource.getConnection();
    }

    @Test
    public void performanceTest() throws UnauthorizedApiKeyException, SQLException {
        long startTime = System.currentTimeMillis();
        testAllApiKeys();
        long endTime = System.currentTimeMillis();
        System.out.println("Performance: " + (endTime - startTime) + " ms");
    }

    private void testAllApiKeys() throws SQLException, UnauthorizedApiKeyException {
        try (Connection connection = createConnection();
             ResultSet rs = connection.prepareStatement("SELECT key FROM %s".formatted(keyTable)).executeQuery()) {
            while (rs.next()) {
                assertNotNull(databaseApiKeyStore.getScopes(rs.getString("key")));
            }
        }
    }

    private void createTables() throws SQLException {
        try (Connection connection = createConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE %s (
                        id SERIAL PRIMARY KEY,
                        key UUID NOT NULL
                    );
                    """.formatted(keyTable.getName()));
            statement.executeUpdate("""
                    CREATE TABLE %s (
                        id SERIAL PRIMARY KEY,
                        key_id INT REFERENCES %s (id),
                        scope VARCHAR(255) NOT NULL
                    );
                    """.formatted(scopeTable.getName()));
        }
        System.out.println("Tables created");
        insertValues();
    }

    private void insertValues() throws SQLException {
        try (Connection connection = createConnection();
             PreparedStatement apiKeyStmt = connection.prepareStatement("""
                     INSERT INTO %s (key) VALUES (?)
                     """.formatted(keyTable.getName()));
             PreparedStatement scopeStmt = connection.prepareStatement("""
                     INSERT INTO %s (key_id, scope) VALUES (?, ?)
                     """.formatted(scopeTable.getName()));
        ) {
            for (int i = 0; i < USERS; i++) {
                String apiKey = UUID.randomUUID().toString();
                apiKeyStmt.setString(1, apiKey);
                apiKeyStmt.executeUpdate();

                scopeStmt.setString(1, "scope" + i);
                scopeStmt.setString(2, apiKey);
                scopeStmt.executeUpdate();
            }
        }
        System.out.println("Values inserted");
    }
}
