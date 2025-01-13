package com.predic8.membrane.core.stores;

import com.predic8.membrane.core.interceptor.apikey.stores.DatabaseApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.KeyTable;
import com.predic8.membrane.core.interceptor.apikey.stores.ScopeTable;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatabaseApiKeyStorePerformanceTest {

    DatabaseApiKeyStore databaseApiKeyStore;
    PGSimpleDataSource dataSource;
    KeyTable keyTable;
    ScopeTable scopeTable;

    @BeforeEach
    void setUp() throws SQLException {
        createDataSource();

        keyTable = new KeyTable();
        scopeTable = new ScopeTable();
        keyTable.setName("key");
        scopeTable.setName("scope");
        databaseApiKeyStore.setKeyTable(keyTable);
        databaseApiKeyStore.setScopeTable(scopeTable);
        createTableIfNotExists();


    }

    private void createDataSource() {
        databaseApiKeyStore = new DatabaseApiKeyStore();
        dataSource = new PGSimpleDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(5432);
        dataSource.setDatabaseName("test");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        databaseApiKeyStore.setDatasource(dataSource);
    }

    @Test
    @Timeout(300)
    public void performanceTest() throws UnauthorizedApiKeyException, SQLException {
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            String apiKey = UUID.randomUUID().toString();
            insertApiKey(apiKey);
            insertScope(apiKey, "scope" + i);
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("generate api keys " + duration + " ms");
        testAllApiKeys();
    }

    private void createTableIfNotExists() throws SQLException {
        String createKeyTableSQL = "CREATE TABLE IF NOT EXISTS key (" +
                "id SERIAL PRIMARY KEY, " +
                "key UUID NOT NULL)";
        String createScopeTableSQL = "CREATE TABLE IF NOT EXISTS scope (" +
                "id SERIAL PRIMARY KEY, " +
                "key_id INT REFERENCES key(id), " +
                "scope VARCHAR(255) NOT NULL);";

        try (Connection connection = dataSource.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(createKeyTableSQL);
                stmt.executeUpdate(createScopeTableSQL);
            }
        }
    }

    private void insertApiKey(String apiKey) throws SQLException {
        String insertKeySQL = "INSERT INTO key (key) VALUES (?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(insertKeySQL)) {
            stmt.setObject(1, UUID.fromString(apiKey));
            stmt.executeUpdate();
        }
    }

    private void insertScope(String apiKey, String scope) throws SQLException {
        String insertScopeSQL = "INSERT INTO scope (key_id, scope) " +
                "SELECT id, ? FROM key WHERE key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(insertScopeSQL)) {
            stmt.setString(1, scope);
            stmt.setObject(2, UUID.fromString(apiKey));
            stmt.executeUpdate();
        }
    }

    private void testAllApiKeys() throws SQLException, UnauthorizedApiKeyException {
        String selectAllKeysSQL = "SELECT key FROM key";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(selectAllKeysSQL);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String apiKey = rs.getString("key");
                assertNotNull(databaseApiKeyStore.getScopes(apiKey));
            }
        }
    }
}
