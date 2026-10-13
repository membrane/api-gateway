/* Copyright 2017 predic8 GmbH, www.predic8.com
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
   http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.session;

import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.test.TestAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static com.predic8.membrane.core.util.SecurityUtils.AlgoSalt;
import static com.predic8.membrane.core.util.SecurityUtils.createPasswdCompatibleHash;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class JdbcUserDataProviderTest {

    private JdbcUserDataProvider provider;
    private Router router;
    private JdbcDataSource dataSource;

    private static final String TABLE_NAME = "users";
    private static final String USER_COLUMN = "username";
    private static final String PASSWORD_COLUMN = "password";
    private static final String TEST_PASSWORD = "secret123";
    private static final String TEST_USER = "alice";

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        router = new DefaultRouter();
        router.getRegistry().register("testDataSource", dataSource);

        provider = new JdbcUserDataProvider();
        provider.setTableName(TABLE_NAME);
        provider.setUserColumnName(USER_COLUMN);
        provider.setPasswordColumnName(PASSWORD_COLUMN);
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Clean up database
        if (dataSource != null) {
            try (Connection con = dataSource.getConnection();
                 Statement stmt = con.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            }
        }
    }

    @Test
    void initCreatesTable() throws SQLException {
        provider.init(router);

        // Verify table was created
        try (var con = dataSource.getConnection();
             var rs = con.getMetaData().getTables(null, null, TABLE_NAME.toUpperCase(), null)) {
            assertTrue(rs.next(), "Table should be created");
        }
    }

    @Test
    void initWithExplicitDataSource() throws SQLException {
        provider.setDatasource(dataSource);
        provider.init(router);

        assertNotNull(provider.getDatasource());

        // Verify table exists
        try (var con = dataSource.getConnection();
             var rs = con.getMetaData().getTables(null, null, TABLE_NAME.toUpperCase(), null)) {
            assertTrue(rs.next());
        }
    }

    @Test
    void initSkipsCreationWhenTableExists() throws SQLException {
        createUsersTable(dataSource);
        provider.setDatasource(rejectingCreateTable(dataSource));

        assertLogsTableExistsWithoutError(captureLog(() -> provider.init(router)));
    }

    @Test
    void initSkipsCreationWhenTableExistsInLowerCaseDatabase() throws SQLException {
        // PostgreSQL folds unquoted identifiers to lower case
        final var lowerCaseDataSource = new JdbcDataSource();
        lowerCaseDataSource.setURL("jdbc:h2:mem:lower;DATABASE_TO_LOWER=TRUE");
        lowerCaseDataSource.setUser("sa");
        lowerCaseDataSource.setPassword("");

        try (var ignored = lowerCaseDataSource.getConnection()) { // keeps the in-memory database alive
            createUsersTable(lowerCaseDataSource);
            provider.setDatasource(rejectingCreateTable(lowerCaseDataSource));

            assertLogsTableExistsWithoutError(captureLog(() -> provider.init(router)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",                         // stores upper case, like Oracle
            ";DATABASE_TO_LOWER=TRUE",  // stores lower case, like PostgreSQL
            ";DATABASE_TO_UPPER=FALSE"  // stores as written, like MySQL on Linux
    })
    void secondInitFindsTableCreatedByFirst(String mode) throws SQLException {
        final var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:restart" + mode);
        ds.setUser("sa");
        ds.setPassword("");

        try (var ignored = ds.getConnection()) { // keeps the in-memory database alive
            provider.setDatasource(ds);
            final var firstStart = captureLog(() -> provider.init(router));
            assertFalse(firstStart.contains("Error creating table"), firstStart.getMessages()::toString);

            assertLogsTableExistsWithoutError(captureLog(() -> provider.init(router)));
        }
    }

    @Test
    void initWithoutDataSourceThrowsException() {
        provider.init(router);

        assertThrows(RuntimeException.class, () -> {
            // Trigger datasource lookup
            provider.verify(Map.of("username", "test", "password", "test"));
        });
    }

    @Test
    void tableNameSanitization() {
        assertThrows(IllegalArgumentException.class, () -> {
            provider.setTableName("users; DROP TABLE users;");
            provider.init(router);
        });
    }

    @Test
    void userColumnNameSanitization() {
        assertThrows(IllegalArgumentException.class, () -> {
            provider.setUserColumnName("username'; DROP TABLE users; --");
            provider.init(router);
        });
    }

    @Test
    void passwordColumnNameSanitization() {
        assertThrows(IllegalArgumentException.class, () -> {
            provider.setPasswordColumnName("password' OR '1'='1");
            provider.init(router);
        });
    }

    @Test
    void validIdentifierNames() {
        provider.setTableName("Valid_Table_123");
        provider.setUserColumnName("user_name_1");
        provider.setPasswordColumnName("pass_word_2");

        assertDoesNotThrow(() -> provider.init(router));
    }

    @Test
    void verifyWithHashedPassword() throws SQLException {
        provider.init(router);

        // Insert user with hashed password
        var hashedPassword = createPasswdCompatibleHash(new AlgoSalt("6", "saltsalt"), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String,String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", TEST_PASSWORD);

        var result = provider.verify(postData);

        assertNotNull(result);
        assertEquals(TEST_USER, result.get(USER_COLUMN.toLowerCase()));
    }

    @Test
    void verifyWithPlainTextPassword() throws SQLException {
        provider.init(router);

        // Insert user with plain text password (not recommended in production!)
        insertUser(TEST_USER, TEST_PASSWORD);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", TEST_PASSWORD);

        var result = provider.verify(postData);

        assertNotNull(result);
        assertEquals(TEST_USER, result.get(USER_COLUMN.toLowerCase()));
    }

    @Test
    void verifyWithWrongPassword() throws SQLException {
        provider.init(router);

        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("6", salt), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", "wrongpassword");

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithNonExistentUser() {
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", "nonexistent");
        postData.put("password", TEST_PASSWORD);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithNullUsername() {
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", null);
        postData.put("password", TEST_PASSWORD);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithMissingUsername() {
        provider.init(router);

        Map<String, String> postData = new HashMap<>();
        postData.put("password", TEST_PASSWORD);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithNullPassword() throws SQLException {
        provider.init(router);
        insertUser(TEST_USER, TEST_PASSWORD);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", null);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyWithMissingPassword() throws SQLException {
        provider.init(router);
        insertUser(TEST_USER, TEST_PASSWORD);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);

        assertThrows(NoSuchElementException.class, () -> provider.verify(postData));
    }

    @Test
    void verifyReturnsAllColumns() throws SQLException {
        provider.init(router);

        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("6", salt), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", TEST_PASSWORD);

        var result = provider.verify(postData);

        // Should contain all columns from the table
        assertEquals(Set.of(USER_COLUMN.toLowerCase(), PASSWORD_COLUMN.toLowerCase()), result.keySet());
    }

    @Test
    void verifyWithSHA256Hash() throws SQLException {
        provider.init(router);

        // SHA-256: $5$salt$hash
        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("5", salt), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", TEST_PASSWORD);

        var result = provider.verify(postData);

        assertNotNull(result);
        assertEquals(TEST_USER, result.get(USER_COLUMN.toLowerCase()));
    }

    @Test
    void verifyWithMD5Hash() throws SQLException {
        provider.init(router);

        // MD5: $1$salt$hash
        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("1", salt), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String, String> postData = new HashMap<>();
        postData.put("username", TEST_USER);
        postData.put("password", TEST_PASSWORD);

        var result = provider.verify(postData);

        assertNotNull(result);
        assertEquals(TEST_USER, result.get(USER_COLUMN.toLowerCase()));
    }

    @Test
    void multipleUsers() throws SQLException {
        provider.init(router);

        String salt = "saltsalt";
        String hash1 = createPasswdCompatibleHash(new AlgoSalt("6", salt), "password1");
        String hash2 = createPasswdCompatibleHash(new AlgoSalt("6", salt), "password2");
        String hash3 = createPasswdCompatibleHash(new AlgoSalt("6", salt), "password3");

        insertUser("alice", hash1);
        insertUser("bob", hash2);
        insertUser("charlie", hash3);

        assertDoesNotThrow(() -> provider.verify(Map.of("username", "alice", "password", "password1")));
        assertDoesNotThrow(() -> provider.verify(Map.of("username", "bob", "password", "password2")));
        assertDoesNotThrow(() -> provider.verify(Map.of("username", "charlie", "password", "password3")));
    }

    @Test
    void verifyWithAdditionalColumns() throws SQLException {
        provider.init(router);

        // Add extra columns to the user
        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("6", salt), TEST_PASSWORD);

        try (Connection con = dataSource.getConnection();
             Statement stmt = con.createStatement()) {
            // Add custom columns
            stmt.execute("ALTER TABLE %s ADD COLUMN email VARCHAR(255)".formatted(TABLE_NAME));
            stmt.execute("ALTER TABLE %s ADD COLUMN role VARCHAR(50)".formatted(TABLE_NAME));
        }

        // Insert user with additional data
        try (Connection con = dataSource.getConnection();
             var ps = con.prepareStatement(
                     "INSERT INTO %s (%s, %s, email, role) VALUES (?, ?, ?, ?)".formatted(TABLE_NAME, USER_COLUMN, PASSWORD_COLUMN))) {
            ps.setString(1, TEST_USER);
            ps.setString(2, hashedPassword);
            ps.setString(3, "alice@example.com");
            ps.setString(4, "admin");
            ps.executeUpdate();
        }

        Map<String, String> postData = Map.of("username", TEST_USER, "password", TEST_PASSWORD);
        Map<String, String> result = provider.verify(postData);

        // Verify additional columns are returned
        assertEquals("alice@example.com", result.get("email"));
        assertEquals("admin", result.get("role"));
    }

    @Test
    void caseInsensitiveColumnNames() throws SQLException {
        provider.init(router);

        String salt = "saltsalt";
        String hashedPassword = createPasswdCompatibleHash(new AlgoSalt("6", salt), TEST_PASSWORD);
        insertUser(TEST_USER, hashedPassword);

        Map<String, String> postData = Map.of("username", TEST_USER, "password", TEST_PASSWORD);
        Map<String, String> result = provider.verify(postData);

        // Column names should be lowercase in result
        assertTrue(result.containsKey(USER_COLUMN.toLowerCase()));
        assertTrue(result.containsKey(PASSWORD_COLUMN.toLowerCase()));
    }

    @Test
    void tableNameConvertedToUpperCase() {
        provider.setTableName("lowercase_table");
        assertEquals("LOWERCASE_TABLE", provider.getTableName());
    }

    @Test
    void userColumnNameConvertedToUpperCase() {
        provider.setUserColumnName("lowercase_user");
        assertEquals("LOWERCASE_USER", provider.getUserColumnName());
    }

    @Test
    void passwordColumnNameConvertedToUpperCase() {
        provider.setPasswordColumnName("lowercase_password");
        assertEquals("LOWERCASE_PASSWORD", provider.getPasswordColumnName());
    }

    private static void assertLogsTableExistsWithoutError(TestAppender log) {
        assertTrue(log.contains("already exists"), log.getMessages()::toString);
        assertFalse(log.contains("Error creating table"), log.getMessages()::toString);
    }

    private static void createUsersTable(DataSource dataSource) throws SQLException {
        try (var con = dataSource.getConnection();
             var stmt = con.createStatement()) {
            stmt.execute("CREATE TABLE %s (%s VARCHAR(255) PRIMARY KEY, %s VARCHAR(255) NOT NULL)".formatted(TABLE_NAME, USER_COLUMN, PASSWORD_COLUMN));
        }
    }

    /**
     * Like PostgreSQL with Membrane's H2-style DDL: every CREATE statement fails.
     */
    private static DataSource rejectingCreateTable(DataSource dataSource) throws SQLException {
        final var ds = mock(DataSource.class);
        when(ds.getConnection()).thenAnswer(i -> rejectingCreateTableConnection(dataSource.getConnection()));
        return ds;
    }

    private static Connection rejectingCreateTableConnection(Connection real) throws SQLException {
        final var con = mock(Connection.class, delegatesTo(real));
        doAnswer(i -> rejectingCreateTableStatement(real.createStatement())).when(con).createStatement();
        return con;
    }

    private static Statement rejectingCreateTableStatement(Statement real) throws SQLException {
        final var stmt = mock(Statement.class, delegatesTo(real));
        doThrow(new SQLException("syntax error at or near \"AUTO_INCREMENT\""))
                .when(stmt).executeUpdate(startsWith("CREATE"));
        return stmt;
    }

    private static TestAppender captureLog(Runnable action) {
        final var root = (Logger) LogManager.getRootLogger();
        final var appender = new TestAppender("JdbcUserDataProviderTest");
        appender.start();
        root.addAppender(appender);
        try {
            action.run();
        } finally {
            root.removeAppender(appender);
            appender.stop();
        }
        return appender;
    }

    // Helper method to insert a user into the database
    private void insertUser(String username, String password) throws SQLException {
        try (var con = dataSource.getConnection();
             var ps = con.prepareStatement(
                     "INSERT INTO %s (%s, %s) VALUES (?, ?)".formatted(TABLE_NAME, USER_COLUMN, PASSWORD_COLUMN))) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
        }
    }
}