/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.evaluation;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.apikey.stores.*;
import org.apache.commons.io.FileUtils;
import org.apache.derby.jdbc.*;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.*;

import static com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * To test the performance of API key lookups in a database
 */
public class JDBCApiKeyStorePerformanceTest {

    private static final Logger LOGGER = Logger.getLogger(JDBCApiKeyStorePerformanceTest.class.getName());
    private static final int USERS = 10;
    private static final String DATABASE_NAME = "test";
    private static final String CREATE_DB_FLAG = "create";
    private final Map<String, Set<String>> keyToScopesMap = new HashMap<>();

    private JDBCApiKeyStore jdbcApiKeyStore;
    private EmbeddedDataSource dataSource;
    private Connection connection;
    private KeyTable keyTable;
    private ScopeTable scopeTable;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        jdbcApiKeyStore = createApiKeyStore();
        connection = getDataSource().getConnection();
        jdbcApiKeyStore.init(new Router());
    }

    @Test
    public void createTableIfNotExistsTest() throws SQLException {
        assertTrue(tableExists(connection, jdbcApiKeyStore.getKeyTable().getName()));
        assertTrue(tableExists(connection, jdbcApiKeyStore.getScopeTable().getName()));
    }


    @Test
    public void performanceTest() throws UnauthorizedApiKeyException, SQLException {
        insertValues();
        long startTime = System.currentTimeMillis();
        validateAllApiKeys();
        long endTime = System.currentTimeMillis();
        LOGGER.info("Performance: " + (endTime - startTime) / 1000 + " seconds");
    }

    private JDBCApiKeyStore createApiKeyStore() {
        JDBCApiKeyStore apiKeyStore = new JDBCApiKeyStore();

        keyTable = new KeyTable();
        keyTable.setName("apikey");

        scopeTable = new ScopeTable();
        scopeTable.setName("scope");

        apiKeyStore.setKeyTable(keyTable);
        apiKeyStore.setScopeTable(scopeTable);
        return apiKeyStore;
    }

    private EmbeddedDataSource getDataSource() throws IOException {
        if (dataSource == null) {
            if (new File(DATABASE_NAME).exists()) {
                FileUtils.deleteDirectory(new File(DATABASE_NAME));
            }
            dataSource = new EmbeddedDataSource();
            dataSource.setDatabaseName(DATABASE_NAME);
            dataSource.setCreateDatabase(CREATE_DB_FLAG);
            jdbcApiKeyStore.setDatasource(dataSource);
        }
        return dataSource;
    }

    private void validateAllApiKeys() throws SQLException, UnauthorizedApiKeyException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT apikey FROM " + keyTable.getName());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            String key = rs.getString("apikey");
            Set<String> scopes = jdbcApiKeyStore.getScopes(key)
                    .orElseThrow(() -> new RuntimeException("No scopes found for key: " + key));
            keyToScopesMap.put(key, scopes);
        }
        for (Map.Entry<String, Set<String>> entry : keyToScopesMap.entrySet()) {
            assertNotNull(entry.getValue(), "Scopes should not be null for key: " + entry.getKey());
        }
    }

    private void insertValues() throws SQLException {
        PreparedStatement apiKeyStmt = connection.prepareStatement(String.format(
                "INSERT INTO %s (apikey) VALUES (?)", keyTable.getName()));
        PreparedStatement scopeStmt = connection.prepareStatement(String.format(
                "INSERT INTO %s (apikey, scope) VALUES (?, ?)", scopeTable.getName()));
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
