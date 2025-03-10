package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.ExchangeState;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.exchange.snapshots.DynamicAbstractExchangeSnapshot;
import com.predic8.membrane.core.exchange.snapshots.RequestSnapshot;
import com.predic8.membrane.core.exchange.snapshots.ResponseSnapshot;
import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.RuleKey;
import com.predic8.membrane.core.proxies.StatisticCollector;
import com.predic8.membrane.core.util.ConfigurationException;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

@MCElement(name = "mongoDBExchangeStore")
public class MongoDBExchangeStore extends AbstractExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(MongoDBExchangeStore.class);

    private String connection;
    private String database;
    private String collectionName;
    private MongoCollection<Document> collection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, AbstractExchangeSnapshot> shortTermMemoryForBatching = new HashMap<>();
    private final Cache<Long, AbstractExchangeSnapshot> cacheToWaitForMongoIndex =
            CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();

    @Override
    public void init(Router router) {
        try {
            collection = MongoClients.create(connection).getDatabase(database).getCollection(collectionName);
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            log.info("MongoDB initialized with database '{}' and collection '{}'", database, collectionName);
            Thread updateJob = new Thread(() -> {
                while (true) {
                    try {
                        List<AbstractExchangeSnapshot> exchanges;
                        synchronized (shortTermMemoryForBatching) {
                            exchanges = new ArrayList<>(shortTermMemoryForBatching.values());
                            shortTermMemoryForBatching.values().forEach(exc -> cacheToWaitForMongoIndex.put(exc.getId(), exc));
                            shortTermMemoryForBatching.clear();
                        }
                        if (!exchanges.isEmpty()) {
                            storeExchangesInMongo(exchanges);
                        } else {
                            Thread.sleep(1000);
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        log.error("Error in MongoDBExchangeStore update job", e);
                    }
                }
            });
            updateJob.start();
        } catch (Exception e) {
            throw new ConfigurationException(String.format("Failed to initialize MongoDB connection: %s", connection), e);
        }
    }

    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        AbstractExchangeSnapshot excCopy;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = new DynamicAbstractExchangeSnapshot(exc, flow, this::addForMongoDB, BodyCollectingMessageObserver.Strategy.TRUNCATE, 100000);
                addForMongoDB(excCopy);
            } else {
                excCopy = getForMongoDB((int) exc.getId());
                DynamicAbstractExchangeSnapshot.addObservers(exc, excCopy, this::addForMongoDB, flow);
                excCopy = excCopy.updateFrom(exc, flow);
                addForMongoDB(excCopy);
            }
        } catch (Exception e) {
            log.error("Error while processing exchange snapshot", e);
        }
    }

    private void storeExchangesInMongo(List<AbstractExchangeSnapshot> exchanges) {
        List<Document> documents = new ArrayList<>();
        for (AbstractExchangeSnapshot exchange : exchanges) {
            try {
                documents.add(createMongoDocument(exchange));
            } catch (Exception e) {
                log.error("Error converting exchange to MongoDB document", e);
            }
        }
        collection.insertMany(documents);
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

    private void addForMongoDB(AbstractExchangeSnapshot exc) {
        synchronized (shortTermMemoryForBatching) {
            shortTermMemoryForBatching.put(exc.getId(), exc);
        }
    }

    public AbstractExchangeSnapshot getForMongoDB(long id) {
        if (shortTermMemoryForBatching.get(id) != null) {
            return shortTermMemoryForBatching.get(id);
        }
        if (cacheToWaitForMongoIndex.getIfPresent(id) != null) {
            return cacheToWaitForMongoIndex.getIfPresent(id);
        }
        return null;
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        return null;
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
        return null;
    }

    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        return null;
    }

    @Override
    public Object[] getAllExchanges() {
        return null;
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
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
