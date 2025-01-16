package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.jdbc.AbstractJdbcSupport;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@MCElement(name = "databaseApiKeyStore", topLevel = false)
public class DatabaseApiKeyStore extends AbstractJdbcSupport implements ApiKeyStore {

    private KeyTable keyTable;
    private ScopeTable scopeTable;
    private Connection connection;

    @Override
    public void init(Router router) {
        super.init(router);
        try {
            connection = getDatasource().getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        try {
            checkApiKey(apiKey);
            return fetchScopes(apiKey);
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving scopes for API key: " + apiKey, e);
        }
    }

    private void checkApiKey(String apiKey) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM %s WHERE key_value = ?".formatted(keyTable.getName()))) {
            stmt.setString(1, apiKey);
        } catch (SQLException e) {
            createTables();
        }
    }

    private @NotNull Optional<List<String>> fetchScopes(String apiKey) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM %s a,%s s WHERE a.key_value=s.key_value AND a.key_value = ?".formatted(keyTable.getName(), scopeTable.getName()))) {
            stmt.setString(1, apiKey);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> scopes = new ArrayList<>();
                while (rs.next()) {
                    scopes.add(rs.getString("scope"));
                }
                return Optional.of(scopes);
            }
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
    }

    @MCChildElement(order = 0)
    public void setKeyTable(KeyTable keyTable) {
        this.keyTable = keyTable;
    }

    @MCChildElement(order = 1)
    public void setScopeTable(ScopeTable scopeTable) {
        this.scopeTable = scopeTable;
    }

    public KeyTable getKeyTable() {
        return keyTable;
    }

    public ScopeTable getScopeTable() {
        return scopeTable;
    }
}
