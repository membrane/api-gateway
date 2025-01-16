package com.predic8.membrane.core.stores;

import com.predic8.membrane.core.interceptor.apikey.stores.DatabaseApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.KeyTable;
import com.predic8.membrane.core.interceptor.apikey.stores.ScopeTable;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseApiKeyStorePerformanceTest {

    private static final Logger logger = Logger.getLogger(DatabaseApiKeyStorePerformanceTest.class.getName());
    private DatabaseApiKeyStore databaseApiKeyStore;
    private EmbeddedDataSource dataSource;
    private KeyTable keyTable;
    private ScopeTable scopeTable;
    private static final int USERS = 10000;

    @BeforeEach
    void setUp() throws SQLException {
        databaseApiKeyStore = createDatabaseApiKeyStore();
        clearTables();
        createTables();
    }

    private DatabaseApiKeyStore createDatabaseApiKeyStore() {
        DatabaseApiKeyStore apiKeyStore = new DatabaseApiKeyStore();
        keyTable = new KeyTable();
        scopeTable = new ScopeTable();
        keyTable.setName("apikey");
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
        Map<String, List<String>> keyToScopesMap = new HashMap<>();
        try (Connection connection = createConnection();
             ResultSet rs = connection.prepareStatement("SELECT key_value FROM %s".formatted(keyTable.getName())).executeQuery()) {
            while (rs.next()) {
                keyToScopesMap.put(rs.getString("key_value"), databaseApiKeyStore.getScopes(rs.getString("key_value")).orElseThrow(RuntimeException::new));
            }
        }
        for (Map.Entry<String, List<String>> entry : keyToScopesMap.entrySet()) {
            assertNotNull(entry.getValue());
        }
    }

    private void createTables() throws SQLException {
        try (Connection connection = createConnection();
             Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE %s (
                        id INT NOT NULL PRIMARY KEY
                            GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
                        key_value VARCHAR(255) NOT NULL
                    )
                    """.formatted(keyTable.getName()));

            stmt.executeUpdate("""
                    CREATE TABLE %s (
                        id INT NOT NULL PRIMARY KEY
                            GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
                        key_id INT NOT NULL REFERENCES %s (id),
                        scope VARCHAR(255) NOT NULL
                    )
                    """.formatted(scopeTable.getName(), keyTable.getName()));
        }
        insertValues();
    }

    private void clearTables() throws SQLException {
        try (Connection connection = createConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DROP TABLE %s".formatted(scopeTable.getName()));
            stmt.executeUpdate("DROP TABLE %s".formatted(keyTable.getName()));
        }
        logger.info("Tables cleared.");
    }

    private void insertValues() throws SQLException {
        try (Connection connection = createConnection();
             PreparedStatement apiKeyStmt = connection.prepareStatement("""
                     INSERT INTO %s (key_value) VALUES (?)
                     """.formatted(keyTable.getName()), Statement.RETURN_GENERATED_KEYS);
             PreparedStatement scopeStmt = connection.prepareStatement("""
                     INSERT INTO %s (key_id, scope) VALUES (?, ?)
                     """.formatted(scopeTable.getName()))
        ) {
            for (int i = 0; i < USERS; i++) {
                apiKeyStmt.setString(1, UUID.randomUUID().toString());
                apiKeyStmt.executeUpdate();

                try (ResultSet generatedKeys = apiKeyStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {

                        scopeStmt.setInt(1, generatedKeys.getInt(1));
                        scopeStmt.setString(2, "scope" + i);
                        scopeStmt.executeUpdate();
                    }
                }
            }
        }
        logger.info("Values inserted.");
    }

}
