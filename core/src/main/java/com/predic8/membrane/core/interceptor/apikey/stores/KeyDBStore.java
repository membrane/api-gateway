package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@MCElement(name = "keyDBStore", topLevel = false)
public class KeyDBStore implements ApiKeyStore {

    private DataSource ds;
    private String dataSource;
    private KeyTable keyTable;
    private ScopeTable scopeTable;

    @Override
    public void init(Router router) {
        this.ds = router.getBeanFactory().getBean(dataSource, DataSource.class);
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        try (Connection connection = ds.getConnection()) {
            checkApiKey(apiKey, connection);
            return fetchScopes(apiKey, connection);
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving scopes for API key: " + apiKey, e);
        }
    }

    private @NotNull Optional<List<String>> fetchScopes(String apiKey, Connection connection) throws SQLException {
        try (PreparedStatement getScopesStmt = connection.prepareStatement("SELECT * FROM " + keyTable.getName() + "," + scopeTable.getName() + " WHERE key.id=key_id AND key.key = ?")) {
            getScopesStmt.setString(1, apiKey);
            try (ResultSet rs = getScopesStmt.executeQuery()) {
                List<String> scopes = new ArrayList<>();
                while (rs.next()) {
                    scopes.add(rs.getString("scope"));
                }
                return Optional.of(scopes);
            }
        }
    }

    private void checkApiKey(String apiKey, Connection connection) throws SQLException, UnauthorizedApiKeyException {
        try (PreparedStatement checkApiKeyStmt = connection.prepareStatement("SELECT * FROM " + keyTable.getName() + " WHERE key = ?")) {
            checkApiKeyStmt.setString(1, apiKey);
            try (ResultSet rs = checkApiKeyStmt.executeQuery()) {
                if (!rs.next()) {
                    throw new UnauthorizedApiKeyException();
                }
            }
        }
    }

    @MCAttribute
    public void setDatasource(String dataSource) {
        this.dataSource = dataSource;
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

    public String getDatasource() {
        return dataSource;
    }
}
