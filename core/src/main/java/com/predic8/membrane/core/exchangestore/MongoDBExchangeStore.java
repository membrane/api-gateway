package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.exchange.snapshots.DynamicAbstractExchangeSnapshot;
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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

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
            MongoClient mongoClient = MongoClients.create(connection);
            MongoDatabase mongoDatabase = mongoClient.getDatabase(database);
            collection = mongoDatabase.getCollection(collectionName);

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

    private void storeExchangesInMongo(List<AbstractExchangeSnapshot> exchanges) {
        List<Document> documents = new ArrayList<>();
        for (AbstractExchangeSnapshot exchange : exchanges) {
            try {
                Document doc = createMongoDocument(exchange);
                documents.add(doc);
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
            log.info("Request Body: {}", exchange.getRequest().toRequest().getBodyAsStringDecoded());

            Document responseDoc = new Document();
            responseDoc.append("status", exchange.getResponse() != null ? exchange.getResponse().getStatusCode() : 0);
            responseDoc.append("headers", exchange.getResponse() != null ? exchange.getResponse().getHeader() : "{}");
            responseDoc.append("body", exchange.getResponse() != null ? exchange.getResponse().toResponse().getBodyAsStringDecoded() : "{}");
            log.info("Response Body: {}", exchange.getResponse().toResponse().getBodyAsStringDecoded());

            doc.append("request", requestDoc);
            doc.append("response", responseDoc);

        } catch (Exception e) {
            log.error("Error serializing request/response to JSON", e);
        }

        return doc;
    }


    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        AbstractExchangeSnapshot excCopy;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = new DynamicAbstractExchangeSnapshot(exc, flow, this::addForMongoDB, BodyCollectingMessageObserver.Strategy.TRUNCATE, 100000);
            } else {
                excCopy = cacheToWaitForMongoIndex.get(exc.getId(), () -> new DynamicAbstractExchangeSnapshot(exc, flow, this::addForMongoDB, BodyCollectingMessageObserver.Strategy.TRUNCATE, 100000));
            }
            excCopy = excCopy.updateFrom(exc, flow);
            addForMongoDB(excCopy);
        } catch (Exception e) {
            log.error("Error while processing exchange snapshot", e);
        }
    }

    private void addForMongoDB(AbstractExchangeSnapshot exc) {
        synchronized (shortTermMemoryForBatching) {
            shortTermMemoryForBatching.put(exc.getId(), exc);
        }
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        Document result = collection.find(Filters.eq("id", id)).first();
        if (result != null) {
            try {
                return objectMapper.readValue(result.toJson(), AbstractExchange.class);
            } catch (Exception e) {
                log.error("Error converting JSON to Exchange", e);
            }
        }
        return null;
    }

    @Override
    public void remove(AbstractExchange exchange) {
        collection.deleteOne(Filters.eq("id", exchange.getId()));
        log.info("Exchange removed from MongoDB: {}", exchange.getId());
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {
        collection.deleteMany(Filters.eq("proxy", proxy.getName()));
        log.info("All exchanges for proxy '{}' removed from MongoDB", proxy.getName());
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        List<Long> ids = new ArrayList<>();
        for (AbstractExchange exchange : exchanges) {
            ids.add(exchange.getId());
        }
        collection.deleteMany(Filters.in("id", ids));
        log.info("Removed {} exchanges from MongoDB", ids.size());
    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        List<AbstractExchange> exchanges = new ArrayList<>();
        for (Document doc : collection.find(Filters.eq("ruleKey", ruleKey.toString()))) {
            try {
                exchanges.add(objectMapper.readValue(doc.toJson(), AbstractExchange.class));
            } catch (Exception e) {
                log.error("Error converting JSON to Exchange", e);
            }
        }
        return exchanges.toArray(new AbstractExchange[0]);
    }

    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        return new StatisticCollector(false);
    }

    @Override
    public Object[] getAllExchanges() {
        return getAllExchangesAsList().toArray();
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        List<AbstractExchange> exchanges = new ArrayList<>();
        for (Document doc : collection.find()) {
            try {
                exchanges.add(objectMapper.readValue(doc.toJson(), AbstractExchange.class));
            } catch (Exception e) {
                log.error("Error converting JSON to Exchange", e);
            }
        }
        return exchanges;
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
