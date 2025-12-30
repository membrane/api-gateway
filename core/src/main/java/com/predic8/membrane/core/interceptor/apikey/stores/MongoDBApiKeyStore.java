/* Copyright 2014 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.apikey.stores;

import com.mongodb.client.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.util.*;
import org.bson.*;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

/**
 * @description Uses a MongoDB collection as a store for API keys and their scopes.
 * Each document in the collection must use the API key as its <code>_id}</code>
 * and may define an array field <code>scopes</code> listing the allowed scopes.
 * <p>
 * Example MongoDB document:
 * </p>
 * <pre>
 * {
 *   "_id": "123456",
 *   "scopes": ["read", "write"]
 * }
 * </pre>
 * <p>
 * Configuration example:
 * </p>
 * <pre><code><apiKey>
 *   <mongoDBApiKeyStore
 *       connection="mongodb://localhost:27017"
 *       database="security"
 *       collection="apikeys"/>
 * </apiKey></code></pre>
 * @topic 3. Security and Validation
 */
@MCElement(name = "mongoDBApiKeyStore")
public class MongoDBApiKeyStore implements ApiKeyStore {

    private String connection;
    private String database;
    private String collection;

    private MongoDatabase mongoDatabase;

    @Override
    public void init(Router router) {
        try {
            mongoDatabase = MongoClients.create(connection).getDatabase(database);
        } catch (Exception e) {
            throw new ConfigurationException("""
                            Failed to initialize MongoDB connection.
                            Please check the connection string: %s
                    """.formatted(connection), e);
        }
    }

    @Override
    public Optional<Set<String>> getScopes(String apiKey) throws UnauthorizedApiKeyException {
        Document apiKeyDoc = mongoDatabase.getCollection(collection).find(eq("_id", apiKey)).first();

        if (apiKeyDoc == null) {
            throw new UnauthorizedApiKeyException();
        }

        return Optional.of(new HashSet<>(apiKeyDoc.getList("scopes", String.class)));
    }

    /**
     * @description MongoDB connection string.
     * @example mongodb://localhost:27017
     */
    @MCAttribute()
    public void setConnection(String connection) {
        this.connection = connection;
    }

    /**
     * @description The database name where API keys are stored.
     * @example security
     */
    @MCAttribute()
    public void setDatabase(String database) {
        this.database = database;
    }

    /**
     * @description The collection name within the database containing the API key documents.
     * @example apikeys
     */
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
