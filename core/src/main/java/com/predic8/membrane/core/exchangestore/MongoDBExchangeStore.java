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
    public void init(Router router) {
        super.init(router);
        if (this.collection == null) {
            this.collection = MongoClients.create(connection)
                    .getDatabase(database)
                    .getCollection(collectionName);
        }
    }

    @Override
    protected void writeToStore(List<AbstractExchangeSnapshot> exchanges) {
        collection = MongoClients.create(connection).getDatabase(database).getCollection(collectionName);
        List<Document> documents = new ArrayList<>();
        for (AbstractExchangeSnapshot exchange : exchanges) {
            try {
                Document doc = exchangeDoc(exchange);
                if (doc!=null)
                    documents.add(doc);
            } catch (Exception e) {
                log.error("Error converting exchange to MongoDB document", e);
            }
        }
        if (!documents.isEmpty()) {
            collection.insertMany(documents);
        }
    }

    private Document exchangeDoc(AbstractExchangeSnapshot exchange) {
        try {
            return Document.parse(objectMapper.writeValueAsString(exchange));
        } catch (JsonProcessingException e) {
            log.error("Error converting exchange to JSON", e);
            return null;
        }
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
        return new ArrayList<>(collection.find().into(new ArrayList<>())).stream().map(doc -> convertMongoJSONToAbstractExchange(doc).toAbstractExchange()).toArray(AbstractExchange[]::new);
    }


    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
       return new ArrayList<>(collection.find().into(new ArrayList<>())).stream().map(doc -> convertMongoJSONToAbstractExchange(doc).toAbstractExchange()).toList();
    }

    private static AbstractExchangeSnapshot convertMongoJSONToAbstractExchange(Document doc) {
        AbstractExchangeSnapshot result;
        try {
            System.out.println("doc = " + doc.toJson());
            System.out.println("==============================");
            System.out.println("objectMapper.writeValueAsString(doc.toJson()) = " + objectMapper.writeValueAsString(doc.toJson()));


            result = objectMapper.readValue(doc.toJson(), AbstractExchangeSnapshot.class);
        } catch (Exception e) {
            log.error("Error converting MongoDB document to AbstractExchangeSnapshot", e);
            throw new RuntimeException(e);
        }
        return Objects.requireNonNull(result);
    }

    @Override
    public void collect(ExchangeCollector collector) {
        for (Document doc : collection.find().into(new ArrayList<>())) {
            collector.collect(convertMongoJSONToAbstractExchange(doc).toAbstractExchange());
        }
    }


    @Override
    public AbstractExchange getExchangeById(long id) {
        return getFromStoreById(id).toAbstractExchange();
    }

    @Override
    public AbstractExchangeSnapshot getFromStoreById(long id) {
        return convertMongoJSONToAbstractExchange(Objects.requireNonNull(collection.find(eq("id", id)).first()));
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
