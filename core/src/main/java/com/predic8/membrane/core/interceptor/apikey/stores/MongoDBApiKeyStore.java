package com.predic8.membrane.core.interceptor.apikey.stores;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@MCElement(name = "mongoDBApiKeyStore", topLevel = false)
public class MongoDBApiKeyStore implements ApiKeyStore {

    private String connectionString;
    private String databaseName;
    private KeyCollection keyCollection;

    private MongoClient mongoClient;
    private MongoDatabase database;

    @Override
    public void init(Router router) {
        try {
            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
        } catch (Exception e) {
            throw new ConfigurationException("Failed to connect to MongoDB", e);
        }
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        Document apiKeyDoc = database.getCollection(keyCollection.getName())
                .find(Filters.eq("_id", apiKey)).first();

        if (apiKeyDoc == null) {
            throw new UnauthorizedApiKeyException();
        }

        return Optional.ofNullable(apiKeyDoc.getList("scopes", String.class));
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

    public String getConnectionString() {
        return connectionString;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getKeyCollection() {
        return keyCollection.getName();
    }
}
