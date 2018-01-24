package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.DynamicAbstractExchangeSnapshot;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@MCElement(name="elasticSearchExchangeStore")
public class ElasticSearchExchangeStore extends AbstractExchangeStore {

    HttpClient client;
    static Logger log = LoggerFactory.getLogger(ElasticSearchExchangeStore.class);
    String elasticSearchBasePath = null;
    int updateIntervalMs = 1000;
    Map<Long,AbstractExchangeSnapshot> shortTermMemoryForBatching = new HashMap<>();
    Thread updateJob;
    String clientId;
    String clientSecret;
    String index = "membrane";
    String type = "exchanges";
    ObjectMapper mapper;
    AtomicLong id = new AtomicLong(0);

    String location = "http://zambia";
    int port = 9200;
    private String localHostname;
    private long startTime;
    boolean init = false;

    @Override
    public void init() {
        super.init();
        if(client == null)
            client = new HttpClient();
        if(mapper == null)
            mapper = new ObjectMapper();

        localHostname = getLocalHostname();
        startTime = System.nanoTime();

        updateJob = new Thread(() -> {
            while(true) {
                try {
                    List<AbstractExchangeSnapshot> exchanges;
                    synchronized (shortTermMemoryForBatching){
                        exchanges = shortTermMemoryForBatching.values().stream().collect(Collectors.toList());
                        shortTermMemoryForBatching.clear();
                    }
                    if(exchanges.size() > 0){
                        sendToElasticSearch(exchanges);
                    }
                    else
                        Thread.sleep(updateIntervalMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                } catch(Exception e){
                    throw new RuntimeException(e);
                }
            }
        });
        updateJob.start();
        init = true;
    }

    private void sendToElasticSearch(List<AbstractExchangeSnapshot> exchanges) throws Exception {
        long myId = id.getAndIncrement();
        StringBuilder data = exchanges
                .stream()
                .map(exchange -> wrapForBulkOperationElasticSearch(index,type,getLocalMachineNameWithSuffix()+"-"+myId,collectExchangeDataFrom(exchange)))
                .collect(StringBuilder::new, (sb, str) -> sb.append(str), (sb1,sb2) -> sb1.append(sb2));

        Exchange elasticSearchExc = new Request.Builder()
                .post(location + ":" + port + "/_bulk")
                .header("Content-Type","application/x-ndjson")
                .body(data.toString())
                .buildExchange();
        elasticSearchExc = client.call(elasticSearchExc);
    }

    private static String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                return IOUtils.toString(Runtime.getRuntime().exec("hostname").getInputStream());
            } catch (IOException e1) {
                e1.printStackTrace();
                return "localhost";
            }
        }
    }

    private String getLocalMachineNameWithSuffix() {
        return localHostname + "-" + startTime;
    }

    public String wrapForBulkOperationElasticSearch(String index, String type, String id,String value){
        return "{ \"index\" : { \"_index\" : \"" + index + "\", \"_type\" : \"" + type + "\", \"_id\" : \""+id+"\" } }\n" + value + "\n";
    }

    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        if(!init)
            init();
        if(elasticSearchBasePath == null)
            initElasticSearchBasePath(exc);

        DynamicAbstractExchangeSnapshot excCopy = null;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = new DynamicAbstractExchangeSnapshot(exc,this::addForElasticSearch);
                addForElasticSearch(excCopy);
            }
            else {
                excCopy = getExchangeDtoById((int) exc.getId()).updateFrom(exc);
                addForElasticSearch(excCopy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void initElasticSearchBasePath(AbstractExchange exc) {
        elasticSearchBasePath = "/membrane/" + exc.getPublicUrl() + "/exchanges/";
    }

    private void addForElasticSearch(AbstractExchangeSnapshot exc) {
        synchronized (shortTermMemoryForBatching){
            shortTermMemoryForBatching.put(exc.getId(),exc);
        }
    }

    private String collectExchangeDataFrom(AbstractExchangeSnapshot exc) {
        try {
            return mapper.writeValueAsString(exc);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public AbstractExchangeSnapshot getExchangeDtoById(int id){
        Long idBox = Long.valueOf(id);
        if(shortTermMemoryForBatching.get(idBox) != null)
            return shortTermMemoryForBatching.get(idBox);

        return getFromElasticSearchById(id);
    }

    private AbstractExchangeSnapshot getFromElasticSearchById(int id) {
        try {
            Exchange exc = new Request.Builder()
                    .post(location + ":" + port + "/" + index + "/" + type + "/_search")
                    .body("{\"query\": {\n" +
                            "\"match\":{\n" +
                            "\"id\": \""+ id +"\"\n" +
                            "}\n" +
                            "}\n" +
                            "}")
                    .header("Content-Type","application/json")
                    .buildExchange();
            exc = client.call(exc);
            Map res = mapper.readValue(exc.getResponse().getBodyAsStringDecoded(),Map.class);
            Map excJson = (Map)((Map)((List)((Map)res.get("hits")).get("hits")).get(0)).get("_source");
            return mapper.readValue(mapper.writeValueAsString(excJson),AbstractExchangeSnapshot.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(AbstractExchange exchange) {

    }

    @Override
    public void removeAllExchanges(Rule rule) {

    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {

    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        return new AbstractExchange[0];
    }

    @Override
    public int getNumberOfExchanges(RuleKey ruleKey) {
        return 0;
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
        return null;
    }

    public HttpClient getClient() {
        return client;
    }

    @MCAttribute
    public void setClient(HttpClient client) {
        this.client = client;
    }

    public int getUpdateIntervalMs() {
        return updateIntervalMs;
    }

    @MCAttribute
    public void setUpdateIntervalMs(int updateIntervalMs) {
        this.updateIntervalMs = updateIntervalMs;
    }

    public String getClientId() {
        return clientId;
    }

    @MCAttribute
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    @MCAttribute
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getLocation() {
        return location;
    }

    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public int getPort() {
        return port;
    }

    @MCAttribute
    public void setPort(int port) {
        this.port = port;
    }
}
