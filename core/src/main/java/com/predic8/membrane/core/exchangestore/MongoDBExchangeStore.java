package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import com.predic8.membrane.core.proxies.StatisticCollector;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@MCElement(name="mongoDBExchangeStore")
public class MongoDBExchangeStore extends AbstractExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoDBExchangeStore.class);

    private String connection;
    private String database;
    private String collection;

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init(Router router) {
        try {
            mongoClient = MongoClients.create(connection);
            mongoDatabase = mongoClient.getDatabase(database);
            log.info("mongodb initialized!");
        } catch (Exception e) {
            throw new ConfigurationException("""
                            Failed to initialize MongoDB connection.
                            Please check the connection string: %s
                    """.formatted(connection), e);
        }
    }

    @Override
    public void snap(AbstractExchange exchange, Interceptor.Flow flow) {
        try {
            mongoDatabase.getCollection(collection).insertOne(Document.parse(objectMapper.writeValueAsString(exchange)));
            log.info("Exchange saved to MongoDB: " + exchange.getId());
        } catch (IOException e) {
            log.error("Error while converting exchange to JSON", e);
        }
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        Document result = mongoDatabase.getCollection(collection).find(new Document("id", id)).first();
        if (result != null) {
            try {
                return objectMapper.readValue(result.toJson(), AbstractExchange.class);
            } catch (IOException e) {
                log.error("Error while converting JSON to Exchange", e);
            }
        }
        return null;
    }

    @Override
    public void remove(AbstractExchange exchange) {
        mongoDatabase.getCollection(collection).deleteOne(new Document("id", exchange.getId()));
        log.info("Exchange removed from MongoDB: " + exchange.getId());
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {
        mongoDatabase.getCollection(collection).deleteMany(new Document("proxy", proxy.getName()));
        log.info("All exchanges for proxy " + proxy.getName() + " removed from MongoDB");
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        for (AbstractExchange exchange : exchanges) {
            remove(exchange);
        }
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

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        return new AbstractExchange[0];
    }

    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        return null;
    }

    @Override
    public Object[] getAllExchanges() {
        return new Object[0];
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        return List.of();
    }
}
