package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

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

    private Document exchangeDoc(AbstractExchangeSnapshot exchange) throws JsonProcessingException {
        Document doc = new Document();
        doc.append("_id", new ObjectId());
        doc.append("id", exchange.getId());
        doc.append("method", exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod() : "UNKNOWN");
        doc.append("listenPort", exchange.getRule().getKey().getPort() != 0 ? exchange.getRule().getKey().getPort() : 0);
        doc.append("protocol", exchange.toAbstractExchange().getRequest().getVersion() != null ? exchange.toAbstractExchange().getRequest().getVersion() : "UNKNOWN" );
        doc.append("client", getClientAddr(false, exchange.toAbstractExchange()) != null ? getClientAddr(false, exchange.toAbstractExchange()) : "UNKNOWN");
        doc.append("server", exchange.getServer() != null ? exchange.getServer() : "UNKNOWN");
        doc.append("proxy", exchange.toAbstractExchange() != null ? objectMapper.writeValueAsString(exchange.toAbstractExchange().getProxy().getKey())  : "UNKNOWN");
        doc.append("timestamp", exchange.getTime().getTime());
        doc.append("requestUri", exchange.getOriginalRequestUri() != null ? exchange.getOriginalRequestUri() : "UNKNOWN" );
        doc.append("status", exchange.getStatus() != null ? exchange.getStatus() : 0);
        doc.append("request", requestDoc(exchange));
        doc.append("response", responseDoc(exchange));
        return doc;
    }

    private static Document requestDoc(AbstractExchangeSnapshot exchange) {
        Document requestDoc = new Document();
        requestDoc.append("method", exchange.getRequest() != null ? exchange.getRequest().toRequest().getMethod() : "UNKNOWN");
        requestDoc.append("header", exchange.getRequest() != null ? exchange.getRequest().getHeader() : "{}");
        requestDoc.append("reqContentLength", exchange.toAbstractExchange() != null ? exchange.toAbstractExchange().getRequestContentLength() : 0);
        requestDoc.append("reqContentType", exchange.toAbstractExchange().getRequestContentType() != null ? exchange.toAbstractExchange().getRequestContentType() : "UNKNOWN");
        requestDoc.append("body", exchange.getRequest() != null ? Base64.getEncoder().encodeToString(exchange.getRequest().toRequest().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)) : "{}");
        return requestDoc;
    }

    private static Document responseDoc(AbstractExchangeSnapshot exchange) {
        Document responseDoc = new Document();
        responseDoc.append("status", exchange.getResponse() != null ? exchange.getResponse().getStatusCode() : 0);
        responseDoc.append("header", exchange.getResponse() != null ? exchange.getResponse().getHeader() : "{}");
        responseDoc.append("body", exchange.getResponse() != null ? Base64.getEncoder().encodeToString(exchange.getResponse().toResponse().getBodyAsStringDecoded().getBytes(StandardCharsets.UTF_8)) : "{}");
        responseDoc.append("respContentLength", exchange.toAbstractExchange() == null ? 0 : exchange.toAbstractExchange().getResponseContentLength());
        responseDoc.append("respContentType", exchange.toAbstractExchange().getResponseContentType());
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
        return new ArrayList<>(collection.find().into(new ArrayList<>())).stream().map(doc -> {
            AbstractExchangeSnapshot result;
            try {
                result = objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchangeSnapshot.class);
            } catch (Exception e) {
                log.error("Error converting MongoDB document to AbstractExchangeSnapshot", e);
                throw new RuntimeException(e);
            }
            return Objects.requireNonNull(result).toAbstractExchange();
        }).toArray(AbstractExchange[]::new);
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        ensureCollectionIsInitialized();
        return getTotals();
    }

    @Override
    public void collect(ExchangeCollector collector) {
        ensureCollectionIsInitialized();
        for (Document doc : collection.find().into(new ArrayList<>())) {
            try {
                collector.collect(objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchangeSnapshot.class).toAbstractExchange());
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
