package com.predic8.membrane.core.interceptor.apikey.stores;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@MCElement(name = "mongoDBApiKeyStore", topLevel = false)
public class MongoDBApiKeyStore implements ApiKeyStore {

    private String connectionString;
    private String databaseName;
    private KeyCollection keyCollection;
    private ScopeCollection scopeCollection;

    private MongoClient mongoClient;
    private MongoDatabase database;

    @Override
    public void init(Router router) {
        try {
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            createCollectionsIfNotExist();
        } catch (Exception e) {
            throw new ConfigurationException("Failed to connect to MongoDB", e);
        }
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        checkApiKey(apiKey);
        return fetchScopes(apiKey);
    }

    private void checkApiKey(String apiKey) throws UnauthorizedApiKeyException {
        if (database.getCollection(keyCollection.getName()).find(Filters.eq("apikey", apiKey)).first() == null) {
            throw new UnauthorizedApiKeyException();
        }
    }

    private Optional<List<String>> fetchScopes(String apiKey) {
        List<String> scopes = new ArrayList<>();
        for (Document doc : database.getCollection(scopeCollection.getName()).find(Filters.eq("apikey", apiKey))) {
            scopes.add(doc.getString("scope"));
        }
        return Optional.of(scopes);
    }

    private void createCollectionsIfNotExist() {
        if (!collectionExists(keyCollection.getName())) {
            database.createCollection(keyCollection.getName());
        }
        if (!collectionExists(scopeCollection.getName())) {
            database.createCollection(scopeCollection.getName());
        }
    }

    private boolean collectionExists(String collectionName) {
        for (String name : database.listCollectionNames()) {
            if (name.equalsIgnoreCase(collectionName)) {
                return true;
            }
        }
        return false;
    }

    @MCAttribute()
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    @MCAttribute()
    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    @MCChildElement(order = 0)
    public void setKeyCollection(KeyCollection keyCollection) {
        this.keyCollection = keyCollection;
    }

    @MCChildElement(order = 1)
    public void setScopeCollection(ScopeCollection scopeCollection) {
        this.scopeCollection = scopeCollection;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getKeyCollection() {
        return keyCollection.getName();
    }

    public String getScopeCollection() {
        return scopeCollection.getName();
    }
}
