package com.predic8.membrane.core.stores;

import com.predic8.membrane.core.interceptor.apikey.stores.DatabaseApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.KeyTable;
import com.predic8.membrane.core.interceptor.apikey.stores.ScopeTable;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DatabaseApiKeyStorePerformanceTest {
    DatabaseApiKeyStore databaseApiKeyStore;
    PGSimpleDataSource dataSource;
    KeyTable keyTable;
    ScopeTable scopeTable;
    static final int USER = 10;

    @BeforeEach
    void setUp() throws SQLException {
        databaseApiKeyStore = createDatabaseApiKeyStore();
        clearTables();
        createTables();
    }

    private DatabaseApiKeyStore createDatabaseApiKeyStore() {
        DatabaseApiKeyStore apiKeyStore = new DatabaseApiKeyStore();
        apiKeyStore.setDatasource(createDataSource());
        keyTable = new KeyTable();
        scopeTable = new ScopeTable();
        keyTable.setName("key");
        scopeTable.setName("scope");
        apiKeyStore.setKeyTable(keyTable);
        apiKeyStore.setScopeTable(scopeTable);
        return apiKeyStore;
    }

    private DataSource createDataSource() {
        dataSource = new PGSimpleDataSource();
        dataSource.setServerName("localhost");
        dataSource.setPortNumber(5432);
        dataSource.setDatabaseName("postgres");
        dataSource.setUser("user");
        dataSource.setPassword("password");
        return dataSource;
    }

    @Test
    public void performanceTest() throws UnauthorizedApiKeyException, SQLException {
        long startTime = System.currentTimeMillis();
        testAllApiKeys();
        long endTime = System.currentTimeMillis();
        System.out.println("performance " + (endTime - startTime) + " ms");
    }

    private void testAllApiKeys() throws SQLException, UnauthorizedApiKeyException {
        try (Connection connection = dataSource.getConnection(); ResultSet rs = connection.prepareStatement("SELECT key FROM key").executeQuery()) {
            while (rs.next()) {
                assertNotNull(databaseApiKeyStore.getScopes(rs.getString("key")));
            }
        }
    }

    private void createTables() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS key (" + "id SERIAL PRIMARY KEY, " + "key UUID NOT NULL)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS scope (" + "id SERIAL PRIMARY KEY, " + "key_id INT REFERENCES key(id), " + "scope VARCHAR(255) NOT NULL);");
            }
        }
        System.out.println("create tables");
        insertValues();
    }

    private void clearTables() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS %s;".formatted(scopeTable.getName()));
            stmt.executeUpdate("DROP TABLE IF EXISTS %s;".formatted(keyTable.getName()));
        }
        System.out.println("clear tables");
    }

    private void insertValues() throws SQLException {
        for (int i = 0; i < USER; i++) {
            String apiKey = UUID.randomUUID().toString();
            insertApiKey(apiKey);
            insertScope(apiKey, "scope" + i);
        }
        System.out.println("insert values");
    }

    private void insertApiKey(String apiKey) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement("INSERT INTO %s (key) VALUES (?)".formatted(keyTable.getName()))) {
            stmt.setObject(1, UUID.fromString(apiKey));
            stmt.executeUpdate();
        }
    }

    private void insertScope(String apiKey, String scope) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement stmt = connection.prepareStatement("INSERT INTO %s (key_id, scope) SELECT id, ? FROM key WHERE key = ?".formatted(scopeTable.getName()))) {
            stmt.setString(1, scope);
            stmt.setObject(2, UUID.fromString(apiKey));
            stmt.executeUpdate();
        }
    }
}