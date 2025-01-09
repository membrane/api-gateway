package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.jdbc.AbstractJdbcSupport;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@MCElement(name = "databaseApiKeyStore", topLevel = false)
public class DatabaseApiKeyStore extends AbstractJdbcSupport implements ApiKeyStore {

    private KeyTable keyTable;
    private ScopeTable scopeTable;

    @Override
    public void init(Router router) {
        super.init(router);
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        try (Connection connection = getDatasource().getConnection()) {
            checkApiKey(apiKey, connection);
            return fetchScopes(apiKey, connection);
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving scopes for API key: " + apiKey, e);
        }
    }

    private @NotNull Optional<List<String>> fetchScopes(String apiKey, Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM %s,%s WHERE key.id=key_id AND key.key = ?".formatted(keyTable.getName(), scopeTable.getName()))) {
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

    private void checkApiKey(String apiKey, Connection connection) throws SQLException, UnauthorizedApiKeyException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT * FROM %s WHERE key = ?".formatted(keyTable.getName()))) {
            stmt.setString(1, apiKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new UnauthorizedApiKeyException();
                }
            }
        }
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
