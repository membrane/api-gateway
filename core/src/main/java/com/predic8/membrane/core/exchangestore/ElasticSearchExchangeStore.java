/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchange.snapshots.AbstractExchangeSnapshot;
import com.predic8.membrane.core.exchange.AbstractExchange;
import com.predic8.membrane.core.exchange.snapshots.DynamicAbstractExchangeSnapshot;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.RuleKey;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@MCElement(name="elasticSearchExchangeStore")
public class ElasticSearchExchangeStore extends AbstractExchangeStore {

    HttpClient client;
    static Logger log = LoggerFactory.getLogger(ElasticSearchExchangeStore.class);
    int updateIntervalMs = 1000;
    Map<Long,AbstractExchangeSnapshot> shortTermMemoryForBatching = new HashMap<>();
    Cache<Long,AbstractExchangeSnapshot> cacheToWaitForElasticSearchIndex = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.SECONDS).build();
    Thread updateJob;
    String index = "membrane";
    String type = "exchanges";
    ObjectMapper mapper;

    String location = "http://localhost:9200";
    private String documentPrefix;
    private long startTime;
    boolean init = false;
    private int maxBodySize = 100000;
    private BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy = BodyCollectingMessageObserver.Strategy.TRUNCATE;

    @Override
    public void init() {
        super.init();
        if(client == null)
            client = new HttpClient();
        if(mapper == null)
            mapper = new ObjectMapper();

        if(documentPrefix == null)
            documentPrefix = getLocalHostname();
        documentPrefix = documentPrefix.toLowerCase();
        startTime = System.nanoTime();

        updateJob = new Thread(() -> {
            while(true) {
                try {
                    List<AbstractExchangeSnapshot> exchanges;
                    synchronized (shortTermMemoryForBatching){
                        exchanges = shortTermMemoryForBatching.values().stream().collect(Collectors.toList());
                        shortTermMemoryForBatching.values().stream().forEach(exc -> cacheToWaitForElasticSearchIndex.put(exc.getId(),exc));
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
        StringBuilder data = exchanges
                .stream()
                .map(exchange -> wrapForBulkOperationElasticSearch(index,type,getLocalMachineNameWithSuffix()+"-"+exchange.getId(),collectExchangeDataFrom(exchange)))
                .collect(StringBuilder::new, (sb, str) -> sb.append(str), (sb1,sb2) -> sb1.append(sb2));

        Exchange elasticSearchExc = new Request.Builder()
                .post(location + "/_bulk")
                .header("Content-Type","application/x-ndjson")
                .body(data.toString())
                .buildExchange();

        client.call(elasticSearchExc);
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
        return documentPrefix + "-" + startTime;
    }

    public String wrapForBulkOperationElasticSearch(String index, String type, String id,String value){
        return "{ \"index\" : { \"_index\" : \"" + index + "\", \"_type\" : \"" + type + "\", \"_id\" : \""+id+"\" } }\n" + value + "\n";
    }

    @Override
    public void snap(AbstractExchange exc, Interceptor.Flow flow) {
        AbstractExchangeSnapshot excCopy = null;
        try {
            if (flow == Interceptor.Flow.REQUEST) {
                excCopy = new DynamicAbstractExchangeSnapshot(exc, flow, this::addForElasticSearch, bodyExceedingMaxSizeStrategy, maxBodySize);
                addForElasticSearch(excCopy);
            }
            else {
                excCopy = getExchangeDtoById((int) exc.getId());
                DynamicAbstractExchangeSnapshot.addObservers(exc,excCopy,this::addForElasticSearch, flow);
                excCopy = excCopy.updateFrom(exc, flow);
                addForElasticSearch(excCopy);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addForElasticSearch(AbstractExchangeSnapshot exc) {
        synchronized (shortTermMemoryForBatching){
            shortTermMemoryForBatching.put(exc.getId(),exc);
        }
    }

    private String collectExchangeDataFrom(AbstractExchangeSnapshot exc) {
        try {
            Map value = mapper.readValue(mapper.writeValueAsString(exc),Map.class);
            value.put("issuer",documentPrefix);
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public AbstractExchangeSnapshot getExchangeDtoById(int id){
        Long idBox = Long.valueOf(id);
        if(shortTermMemoryForBatching.get(idBox) != null)
            return shortTermMemoryForBatching.get(idBox);
        if(cacheToWaitForElasticSearchIndex.getIfPresent(idBox) != null)
            return cacheToWaitForElasticSearchIndex.getIfPresent(idBox);

        return getFromElasticSearchById(id);
    }

    private AbstractExchangeSnapshot getFromElasticSearchById(long id) {
        try {
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_search")
                    .body("{\n" +
                            "  \"query\": {\n" +
                            "    \"bool\": {\n" +
                            "      \"must\": [\n" +
                            "        {\n" +
                            "          \"wildcard\": {\n" +
                            "            \"issuer\": \""+documentPrefix+"\"\n" +
                            "          }\n" +
                            "        },\n" +
                            "        {\n" +
                            "          \"match\": {\n" +
                            "            \"id\": \""+id+"\"\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}")
                    .header("Content-Type","application/json")
                    .buildExchange();
            exc = client.call(exc);
            Map res = responseToMap(exc);
            Map excJson = getSourceElementFromElasticSearchResponse(res).get(0);
            return mapper.readValue(mapper.writeValueAsString(excJson),AbstractExchangeSnapshot.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getElasticSearchExchangesPath() {
        return location + "/" + index + "/" + type + "/";
    }

    public List<Map> getSourceElementFromElasticSearchResponse(Map response){
        return getSourceElementFromHitsElement(getHitsElementFromElasticSearchResponse(response));
    }

    public List getHitsElementFromElasticSearchResponse(Map response){
        return ((List)((Map)response.get("hits")).get("hits"));
    }

    public List<Map> getSourceElementFromHitsElement(List hits){
        return (List)hits.stream().map(hit -> ((Map)hit).get("_source")).collect(Collectors.toList());
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        return getFromElasticSearchById(id).toAbstractExchange();
    }

    @Override
    public void remove(AbstractExchange exchange) {
        try {
            removeFromElasticSearchById(exchange.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeFromElasticSearchById(long id) throws Exception {
        Exchange exc = new Request.Builder()
                .delete(getElasticSearchExchangesPath() + getLocalMachineNameWithSuffix() + "-" + id)
                .buildExchange();
        client.call(exc);
    }

    @Override
    public void removeAllExchanges(Rule rule) {
        String name = rule.toString();
        try {
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_delete_by_query")
                    .body("{\n" +
                            "  \"query\": {\n" +
                            "    \"bool\": {\n" +
                            "      \"must\": [\n" +
                            "        {\n" +
                            "          \"wildcard\": {\n" +
                            "            \"issuer\": \""+documentPrefix+"\"\n" +
                            "          }\n" +
                            "        },\n" +
                            "        {\n" +
                            "          \"match\": {\n" +
                            "            \"rule.name\": \""+name+"\"\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}")
                    .header("Content-Type","application/json")
                    .buildExchange();
            client.call(exc);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        StringBuilder sb = Stream.of(exchanges).map(exc -> exc.getId()).collect(() -> {
            StringBuilder acc = new StringBuilder();
            acc.append("[");
            return acc;
        },(acc,id) -> acc.append(id).append(","),(acc1,acc2) -> acc1.append(",").append(acc2));
        sb.deleteCharAt(sb.length()-1);
        sb.append("]");
        String exchangeIdsAsJsonArray = sb.toString();

        try {
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_delete_by_query")
                    .body("{\n" +
                            "  \"query\": {\n" +
                            "    \"bool\": {\n" +
                            "      \"must\": [\n" +
                            "        {\n" +
                            "          \"wildcard\": {\n" +
                            "            \"issuer\": \""+documentPrefix+"\"\n" +
                            "          }\n" +
                            "        },\n" +
                            "        {\n" +
                            "          \"terms\": {\n" +
                            "            \"id\": \""+exchangeIdsAsJsonArray+"\"\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}")
                    .header("Content-Type", "application/json")
                    .buildExchange();
            client.call(exc);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        int port = ruleKey.getPort();
        try {
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_search")
                    .body("{\n" +
                            "  \"query\": {\n" +
                            "    \"bool\": {\n" +
                            "      \"must\": [\n" +
                            "        {\n" +
                            "          \"wildcard\": {\n" +
                            "            \"issuer\": \""+documentPrefix+"\"\n" +
                            "          }\n" +
                            "        },\n" +
                            "        {\n" +
                            "          \"match\": {\n" +
                            "            \"rule.port\": \""+port+"\"\n" +
                            "          }\n" +
                            "        }\n" +
                            "      ]\n" +
                            "    }\n" +
                            "  }\n" +
                            "}")
                    .header("Content-Type","application/json")
                    .buildExchange();
            exc = client.call(exc);

            List source = getSourceElementFromElasticSearchResponse(responseToMap(exc));
            AbstractExchangeSnapshot[] snapshots = mapper.readValue(mapper.writeValueAsString(source), AbstractExchangeSnapshot[].class);
            return Stream.of(snapshots).map(snapshot -> snapshot.toAbstractExchange()).collect(Collectors.toList()).toArray(new AbstractExchange[0]);
        }catch (Exception e){
            e.printStackTrace();
            return new AbstractExchange[0];
        }
    }

    @Override
    public int getNumberOfExchanges(RuleKey ruleKey) {
        return getExchanges(ruleKey).length;
    }

    @Override
    public StatisticCollector getStatistics(RuleKey ruleKey) {
        StatisticCollector statistics = new StatisticCollector(false);
        List<AbstractExchange> exchangesList = Arrays.asList(getExchanges(ruleKey));
        if (exchangesList == null || exchangesList.isEmpty())
            return statistics;

        for (int i = 0; i < exchangesList.size(); i++)
            statistics.collectFrom(exchangesList.get(i));

        return statistics;
    }

    @Override
    public Object[] getAllExchanges() {
        return getAllExchangesAsList().toArray();
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        try{
            Exchange exc = new Request.Builder().post(getElasticSearchExchangesPath() + "_search").header("Content-Type","application/json").body("{\n" +
                    "  \"query\": {\n" +
                    "    \"wildcard\": {\n" +
                    "      \"issuer\": \""+ documentPrefix+"\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}").buildExchange();
            exc = client.call(exc);

            if(!exc.getResponse().isOk())
                return new ArrayList<>();

            List sources = getSourceElementFromElasticSearchResponse(responseToMap(exc));
            return (List)sources.stream().map(source -> {
                try {
                    return mapper.readValue(mapper.writeValueAsString(source),AbstractExchangeSnapshot.class).toAbstractExchange();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private Map responseToMap(Exchange exc) throws IOException {
        return mapper.readValue(exc.getResponse().getBodyAsStringDecoded(),Map.class);
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

    public String getLocation() {
        return location;
    }

    /**
     * @description base URL of Elasticsearch
     * @default http://localhost:9200
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    public String getDocumentPrefix() {
        return documentPrefix;
    }

    @MCAttribute
    public void setDocumentPrefix(String documentPrefix) {
        this.documentPrefix = documentPrefix;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    /**
     * @default 100000
     */
    @MCAttribute
    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }

    public BodyCollectingMessageObserver.Strategy getBodyExceedingMaxSizeStrategy() {
        return bodyExceedingMaxSizeStrategy;
    }

    /**
     * @description The strategy to use (TRUNCATE or ERROR) when a HTTP message body is larger than the <tt>maxBodySize</tt>.
     * @default TRUNCATE
     */
    @MCAttribute
    public void setBodyExceedingMaxSizeStrategy(BodyCollectingMessageObserver.Strategy bodyExceedingMaxSizeStrategy) {
        this.bodyExceedingMaxSizeStrategy = bodyExceedingMaxSizeStrategy;
    }
}
