package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.interceptor.statistics.util.JDBCUtil;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.predic8.membrane.core.interceptor.administration.AdminRESTInterceptor.getClientAddr;

@MCElement(name = "mongoDBExchangeStore")
public class MongoDBExchangeStore extends AbstractPersistentExchangeStore {
    private static final Logger log = LoggerFactory.getLogger(MongoDBExchangeStore.class);

    private String connection;
    private String database;
    private String collectionName;
    private MongoCollection<Document> collection;
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    protected void writeToStore(List<AbstractExchangeSnapshot> exchanges) {
        collection = MongoClients.create(connection).getDatabase(database).getCollection(collectionName);
        List<Document> documents = new ArrayList<>();
        for (AbstractExchangeSnapshot exchange : exchanges) {
            try {
                documents.add(exchangeDoc(exchange));
            } catch (Exception e) {
                log.error("Error converting exchange to MongoDB document", e);
            }
        }
        if (!documents.isEmpty()) {
            collection.insertMany(documents);
        }
    }

    private static Document exchangeDoc(AbstractExchangeSnapshot exchange) {
        Document doc = new Document();
        doc.append("_id", new ObjectId());
        doc.append("id", exchange.getId());
        doc.append("method", exchange.getRequest().getMethod());
        doc.append("respContentLength", exchange.toAbstractExchange().getResponseContentLength());
        doc.append("reqContentLength", exchange.toAbstractExchange().getRequestContentLength());
        doc.append("reqContentType", exchange.toAbstractExchange().getRequestContentType());
        doc.append("respContentType", exchange.toAbstractExchange().getResponseContentType());
        doc.append("protocol", exchange.toAbstractExchange().getRequest().getVersion());
        doc.append("client", getClientAddr(false, exchange.toAbstractExchange()));
        doc.append("server", exchange.toAbstractExchange().getServer());
        doc.append("duration", exchange.toAbstractExchange().getTimeReqSent() - exchange.toAbstractExchange().getTimeReqReceived());
        doc.append("timestamp", exchange.getTime().getTime());
        doc.append("msgFilePath", "");
        doc.append("requestUri", exchange.getOriginalRequestUri());
        doc.append("status", exchange.getStatus());
        doc.append("request", requestDoc(exchange));
        doc.append("response", responseDoc(exchange));
        return doc;
    }

    private static Document requestDoc(AbstractExchangeSnapshot exchange) {
        Document requestDoc = new Document();
        requestDoc.append("method", exchange.getRequest() != null ? exchange.getRequest().toRequest().getMethod() : "UNKNOWN");
        requestDoc.append("headers", exchange.getRequest() != null ? exchange.getRequest().getHeader() : "{}");
        requestDoc.append("body", exchange.getRequest() != null ? Base64.getEncoder().encodeToString(exchange.getRequest().toRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)) : "{}");
        return requestDoc;
    }

    private static Document responseDoc(AbstractExchangeSnapshot exchange) {
        Document responseDoc = new Document();
        responseDoc.append("status", exchange.getResponse() != null ? exchange.getResponse().getStatusCode() : 0);
        responseDoc.append("headers", exchange.getResponse() != null ? exchange.getResponse().getHeader() : "{}");
        responseDoc.append("body", exchange.getResponse() != null ? Base64.getEncoder().encodeToString(exchange.getResponse().toResponse().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)) : "{}");
        return responseDoc;
    }

    @Override
    public void remove(AbstractExchange exchange) {
        collection.deleteOne(eq("id", exchange.getId()));
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {
        collection.deleteMany(new Document());
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        List<Bson> filters = new ArrayList<>();
        for (AbstractExchange exchange : exchanges) {
            filters.add(eq("id", exchange.getId()));
        }
        collection.deleteMany(eq("id", filters));
    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        try {
            List<Document> documents = collection.find(eq("key", ruleKey)).into(new ArrayList<>());
            if (documents.isEmpty()) {
                return new AbstractExchange[0];
            }

            return documents.stream()
                    .map(this::convertToExchangeSnapshot)
                    .map(AbstractExchangeSnapshot::toAbstractExchange)
                    .toArray(AbstractExchange[]::new);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AbstractExchangeSnapshot convertToExchangeSnapshot(Document doc) {
        try {
            AbstractExchangeSnapshot snapshot = objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchangeSnapshot.class);
            if (snapshot.getDestinations() == null) {
                snapshot.setDestinations(new ArrayList<>());
            }
            return snapshot;
        } catch (Exception e) {
            log.error("Error converting MongoDB document to AbstractExchangeSnapshot", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        return null;
    }

    @Override
    public void collect(ExchangeCollector collector) {
        ensureCollectionIsInitialized();
        List<Document> docs = collection.find().into(new ArrayList<>());
        for (Document doc : docs) {
            try {
                AbstractExchangeSnapshot snapshot = objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchangeSnapshot.class);
                if (snapshot != null) {
                    collector.collect(snapshot.toAbstractExchange());
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing MongoDB document to exchange", e);
            }
        }
    }

    @Override
    public AbstractExchangeSnapshot getFromStoreById(long id) {
        try {
            Document doc = collection.find(eq("id", id)).first();
            if (doc != null) {
                return objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchangeSnapshot.class);
            }
        } catch (Exception e) {
            log.error("Error retrieving exchange from MongoDB", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    private void ensureCollectionIsInitialized() {
        if (this.collection == null) {
           initMongoConnection();
        }
    }

    public void initMongoConnection() {
        if (this.collection == null) {
            this.collection = MongoClients.create(connection)
                    .getDatabase(database)
                    .getCollection(collectionName);
        }
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
