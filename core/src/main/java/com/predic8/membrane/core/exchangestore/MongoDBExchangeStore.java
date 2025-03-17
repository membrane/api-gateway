package com.predic8.membrane.core.exchangestore;

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

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@MCElement(name = "mongoDBExchangeStore")
public class MongoDBExchangeStore extends AbstractPersistentExchangeStore {
    private static final Logger log = LoggerFactory.getLogger(MongoDBExchangeStore.class);

    private String connection;
    private String database;
    private String collectionName;
    private MongoCollection<Document> collection;
    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        doc.append("timestamp", exchange.getTime().getTime());
        doc.append("requestUri", exchange.getOriginalRequestUri());
        doc.append("status", exchange.getStatus());
        doc.append("request", requestDoc(exchange));
        doc.append("response", responseDoc(exchange));
        return doc;
    }

    private static Document requestDoc(AbstractExchangeSnapshot exchange) {
        Document requestDoc = new Document();
        requestDoc.append("method", exchange.getRequest().toRequest().getMethod());
        requestDoc.append("headers", exchange.getRequest().getHeader());
        requestDoc.append("body", exchange.getRequest().toRequest().getBodyAsStringDecoded());
        return requestDoc;
    }

    private static Document responseDoc(AbstractExchangeSnapshot exchange) {
        Document responseDoc = new Document();
        responseDoc.append("status", exchange.getResponse().getStatusCode());
        responseDoc.append("headers", exchange.getResponse().getHeader());
        responseDoc.append("body", exchange.getResponse().toResponse().getBodyAsStringDecoded());
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
        return collection.find()
                .into(new ArrayList<>())
                .stream()
                .map(this::convertToExchange)
                .toList().toArray(new AbstractExchange[0]);
    }

    private AbstractExchange convertToExchange(Document doc) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(doc), AbstractExchange.class);
        } catch (Exception e) {
            log.error("Error converting MongoDB document to AbstractExchange", e);
            return null;
        }
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        return List.of(getExchanges(null));
    }

    @Override
    public void collect(ExchangeCollector collector) {

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
