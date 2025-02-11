package com.predic8.membrane.core.interceptor.apikey.stores;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;

import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;

@MCElement(name = "mongoDBApiKeyStore", topLevel = false)
public class MongoDBApiKeyStore implements ApiKeyStore {

    private String connection;
    private String database;
    private String collection;

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    @Override
    public void init(Router router) {
        try {
            mongoClient = MongoClients.create(connection);
            mongoDatabase = mongoClient.getDatabase(database);
        } catch (Exception e) {
            throw new ConfigurationException("""
                            Failed to initialize MongoDB connection.
                            Please check the connection string: %s
                    """.formatted(connection), e);
        }
    }

    @Override
    public Optional<List<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        Document apiKeyDoc = mongoDatabase.getCollection(collection).find(eq("_id", apiKey)).first();

        if (apiKeyDoc == null) {
            throw new UnauthorizedApiKeyException();
        }

        return Optional.ofNullable(apiKeyDoc.getList("scopes", String.class));
    }

    @MCAttribute()
    public void setConnection(String connection) {
        this.connection = connection;
    }

    @MCAttribute()
    public void setDatabase(String database) {
        this.database = database;
    }

    @MCAttribute()
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getConnection() {
        return connection;
    }

    public String getDatabase() {
        return database;
    }

    public String getCollection() {
        return collection;
    }
}
