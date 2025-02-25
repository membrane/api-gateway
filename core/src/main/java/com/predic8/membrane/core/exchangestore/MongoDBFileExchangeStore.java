package com.predic8.membrane.core.exchangestore;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import com.predic8.membrane.core.proxies.StatisticCollector;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

@MCElement(name = "mongoDBFileExchangeStore")
public class MongoDBFileExchangeStore extends AbstractExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoDBFileExchangeStore.class);
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

    public void storeExchange(AbstractExchange exchange) {
        try {
            Document document = new Document()
                    .append("request", messageToJson(exchange.getRequest()))
                    .append("response", messageToJson(exchange.getResponse()))
                    .append("timestamp", System.currentTimeMillis());

            mongoDatabase.getCollection(collection).insertOne(document);
            log.info("Exchange saved successfully to MongoDB.");
        } catch (Exception e) {
            log.error("Failed to save exchange to MongoDB", e);
        }
    }

    private String messageToJson(Message message) {
        if (message == null || message.getBody() == null) {
            return "{}";
        }
        try {
            return new String(message.getBody().getContent(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to convert message to JSON", e);
            return "{}";
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
    public void snap(AbstractExchange exchange, Interceptor.Flow flow) {
        storeExchange(exchange);
    }

    @Override
    public void remove(AbstractExchange exchange) {
        throw new UnsupportedOperationException("Method remove() is not supported by MongoExchangeStore");
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {

    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {

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
