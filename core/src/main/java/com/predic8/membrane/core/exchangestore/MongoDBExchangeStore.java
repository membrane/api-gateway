package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "mongoDBExchangeStore")
public class MongoDBExchangeStore extends AbstractPersistentExchangeStore {
    private static final Logger log = LoggerFactory.getLogger(MongoDBExchangeStore.class);

    private String connection;
    private String database;
    private String collectionName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void writeToStore(List<AbstractExchangeSnapshot> exchanges) {
        List<Document> documents = new ArrayList<>();
        for (AbstractExchangeSnapshot exchange : exchanges) {
            try {
                documents.add(createMongoDocument(exchange));
            } catch (Exception e) {
                log.error("Error converting exchange to MongoDB document", e);
            }
        }
        if (!documents.isEmpty()) {
            MongoClients.create(connection).getDatabase(database).getCollection(collectionName).insertMany(documents);
        }
    }

    private Document createMongoDocument(AbstractExchangeSnapshot exchange) {
        Document doc = new Document();
        doc.append("_id", new ObjectId());
        doc.append("id", exchange.getId());
        doc.append("timestamp", exchange.getTime().getTime());
        doc.append("requestUri", exchange.getOriginalRequestUri());
        doc.append("status", exchange.getStatus());

        try {
            Document requestDoc = new Document();
            requestDoc.append("method", exchange.getRequest() != null ? exchange.getRequest().toRequest().getMethod() : "UNKNOWN");
            requestDoc.append("headers", exchange.getRequest() != null ? objectMapper.writeValueAsString(exchange.getRequest().toRequest().getHeader()) : "{}");
            requestDoc.append("body", exchange.getRequest() != null ? exchange.getRequest().toRequest().getBodyAsStringDecoded() : "{}");
            doc.append("request", requestDoc);

            Document responseDoc = new Document();
            responseDoc.append("status", exchange.getResponse() != null ? exchange.getResponse().getStatusCode() : 0);
            responseDoc.append("headers", exchange.getResponse() != null ? exchange.getResponse().getHeader() : "{}");
            responseDoc.append("body", exchange.getResponse() != null ? exchange.getResponse().toResponse().getBodyAsStringDecoded() : "{}");
            doc.append("response", responseDoc);

        } catch (Exception e) {
            log.error("Error serializing request/response to JSON", e);
        }
        return doc;
    }

    @Override
    public void remove(AbstractExchange exchange) {

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
    public List<AbstractExchange> getAllExchangesAsList() {
        return List.of();
    }

    @Override
    public void collect(ExchangeCollector collector) {

    }

    @Override
    public AbstractExchangeSnapshot getFromStoreById(long id) {
        return null;
    }

    @MCAttribute
    public void setConnection(String connection) {
        this.connection = connection;
    }

    @MCAttribute
    public void setDatabase(String database) {
        this.database = database;
    }

    @MCAttribute
    public void setCollection(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getConnection() {
        return connection;
    }

    public String getDatabase() {
        return database;
    }

    public String getCollection() {
        return collectionName;
    }
}
