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
public class JDBCApiKeyStore extends AbstractJdbcSupport implements ApiKeyStore {

    private KeyTable keyTable;
    private ScopeTable scopeTable;

    @Override
    public void init(Router router) {
        super.init(router);
        try {
            createTablesIfNotExist();
        } catch (SQLException e) {
            throw new RuntimeException("Error initializing database tables", e);
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

    private void checkApiKey(String apiKey) throws Exception {
        try (PreparedStatement stmt = getDatasource().getConnection().prepareStatement(
                "SELECT * FROM %s WHERE apikey = ?".formatted(keyTable.getName()))) {
            stmt.setString(1, apiKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new UnauthorizedApiKeyException();
                }
            }
        }
    }

    private @NotNull Optional<List<String>> fetchScopes(String apiKey) throws SQLException {
        try (PreparedStatement stmt = getDatasource().getConnection().prepareStatement("SELECT * FROM %s a,%s s WHERE a.apikey=s.apikey AND a.apikey = ?".formatted(keyTable.getName(), scopeTable.getName()))) {
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

    private void createTablesIfNotExist() throws SQLException {
        try (Connection connection = getDatasource().getConnection()) {
            if (tableExists(connection, keyTable.getName())) {
                connection.createStatement().executeUpdate(String.format("""
                    CREATE TABLE %s (
                        apikey VARCHAR(255) NOT NULL PRIMARY KEY
                    )
                    """, keyTable.getName()));
            }

            if (tableExists(connection, scopeTable.getName())) {
                connection.createStatement().executeUpdate(String.format("""
                    CREATE TABLE %s (
                        apikey VARCHAR(255) NOT NULL REFERENCES %s (apikey),
                        scope VARCHAR(255) NOT NULL
                    )
                    """, scopeTable.getName(), keyTable.getName()));
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        return !connection.getMetaData().getTables(null, null, tableName.toUpperCase(), null).next();
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
