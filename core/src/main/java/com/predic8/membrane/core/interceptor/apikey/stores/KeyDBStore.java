package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.authentication.session.JdbcUserDataProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            try (PreparedStatement checkApiKeyStmt = connection.prepareStatement("SELECT * FROM " + keyTable.getName() + " WHERE key = ?")) {
                checkApiKeyStmt.setString(1, apiKey);
                try (ResultSet rs = checkApiKeyStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new UnauthorizedApiKeyException();
                    }
                }
            }

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
        } catch (Exception e) {
            throw new RuntimeException("Error while retrieving scopes for API key: " + apiKey, e);
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
}
