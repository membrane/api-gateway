package com.predic8.membrane.core.stores;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.apikey.stores.DatabaseApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.stores.KeyTable;
import com.predic8.membrane.core.interceptor.apikey.stores.ScopeTable;
import com.predic8.membrane.core.interceptor.apikey.stores.UnauthorizedApiKeyException;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseApiKeyStorePerformanceTest {

    private static final Logger LOGGER = Logger.getLogger(DatabaseApiKeyStorePerformanceTest.class.getName());
    private static final int USERS = 10;
    private static final String DATABASE_NAME = "test";
    private static final String CREATE_DB_FLAG = "create";
    private Map<String, List<String>> keyToScopesMap = new HashMap<>();

    private DatabaseApiKeyStore databaseApiKeyStore;
    private EmbeddedDataSource dataSource;
    private Connection connection;
    private KeyTable keyTable;
    private ScopeTable scopeTable;

    @BeforeEach
    void setUp() throws SQLException {
        databaseApiKeyStore = createApiKeyStore();
        connection = getDataSource().getConnection();
        clearTablesIfExist();
        createTables();
        databaseApiKeyStore.init(new Router());
    }

    @Test
    public void performanceTest() throws UnauthorizedApiKeyException, SQLException {
        long startTime = System.currentTimeMillis();
        validateAllApiKeys();
        long endTime = System.currentTimeMillis();
        LOGGER.info("Performance: " + (endTime - startTime) / 1000 + " seconds");
    }

    @Test
    public void createTableIfTableDoNotExist() throws UnauthorizedApiKeyException, SQLException {
        clearTablesIfExist();
        databaseApiKeyStore.getScopes("");
        PreparedStatement stmt = connection.prepareStatement("SELECT * FROM %s".formatted(databaseApiKeyStore.getKeyTable().getName()));
        stmt.executeQuery();
        assertTrue(stmt.execute());
    }

    private DatabaseApiKeyStore createApiKeyStore() {
        DatabaseApiKeyStore apiKeyStore = new DatabaseApiKeyStore();

        keyTable = new KeyTable();
        keyTable.setName("apikey");

        scopeTable = new ScopeTable();
        scopeTable.setName("scope");

        apiKeyStore.setKeyTable(keyTable);
        apiKeyStore.setScopeTable(scopeTable);
        return apiKeyStore;
    }

    private EmbeddedDataSource getDataSource() {
        if (dataSource == null) {
            dataSource = new EmbeddedDataSource();
            dataSource.setDatabaseName(DATABASE_NAME);
            dataSource.setCreateDatabase(CREATE_DB_FLAG);
            databaseApiKeyStore.setDatasource(dataSource);
        }
        return dataSource;
    }

    private void validateAllApiKeys() throws SQLException, UnauthorizedApiKeyException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT key_value FROM " + keyTable.getName());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String key = rs.getString("key_value");
            List<String> scopes = databaseApiKeyStore.getScopes(key)
                    .orElseThrow(() -> new RuntimeException("No scopes found for key: " + key));
            keyToScopesMap.put(key, scopes);
        }
        for (Map.Entry<String, List<String>> entry : keyToScopesMap.entrySet()) {
            assertNotNull(entry.getValue(), "Scopes should not be null for key: " + entry.getKey());
        }
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(String.format("""
                CREATE TABLE %s (
                    key_value VARCHAR(255) NOT NULL PRIMARY KEY
                )
                """, keyTable.getName()));

        stmt.executeUpdate(String.format("""
                CREATE TABLE %s (
                    key_value VARCHAR(255) NOT NULL REFERENCES %s (key_value),
                    scope VARCHAR(255) NOT NULL
                )
                """, scopeTable.getName(), keyTable.getName()));
        insertValues();
    }

    private void clearTablesIfExist() throws SQLException {
        Statement stmt = connection.createStatement();
        dropTableIfExists(stmt, scopeTable.getName());
        dropTableIfExists(stmt, keyTable.getName());
    }

    private void dropTableIfExists(Statement stmt, String tableName) {
        try {
            stmt.executeUpdate("DROP TABLE " + tableName);
            LOGGER.info("Table " + tableName + " dropped.");
        } catch (SQLException e) {
            LOGGER.fine("Table " + tableName + " does not exist or could not be dropped: " + e.getMessage());
        }
    }

    private void insertValues() throws SQLException {
        PreparedStatement apiKeyStmt = connection.prepareStatement(String.format(
                "INSERT INTO %s (key_value) VALUES (?)", keyTable.getName()));
        PreparedStatement scopeStmt = connection.prepareStatement(String.format(
                "INSERT INTO %s (key_value, scope) VALUES (?, ?)", scopeTable.getName()));
        for (int i = 0; i < USERS; i++) {
            String keyValue = UUID.randomUUID().toString();
            apiKeyStmt.setString(1, keyValue);
            apiKeyStmt.executeUpdate();

            scopeStmt.setString(1, keyValue);
            scopeStmt.setString(2, "scope" + i);
            scopeStmt.executeUpdate();
        }
        LOGGER.info("Test values inserted into " + keyTable.getName() + " and " + scopeTable.getName());
    }
}
