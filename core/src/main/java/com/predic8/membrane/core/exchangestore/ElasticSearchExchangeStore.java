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

import com.fasterxml.jackson.databind.*;
import com.google.common.collect.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchange.snapshots.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.transport.http.*;
import org.apache.commons.io.*;
import org.json.*;
import org.slf4j.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Request.put;
import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;

/**
 * @description Used for storing exchanges in the Elasticsearch.
 * @explanation Elasticsearch 7 is required. Exchanges can be viewed in admin console and using standard Elasticsearch
 *              tools. Before writing, this class will check if index exists in current Elasticsearch instance. If index does not
 *              exist, it will create index and set up mapping for data types. If the existing index already have mapping this step
 *              will be skipped in order to not to overwrite existing mapping.
 * @topic 4. Monitoring, Logging and Statistics
 */
@MCElement(name="elasticSearchExchangeStore")
public class ElasticSearchExchangeStore extends AbstractPersistentExchangeStore {

    private HttpClient client;
    private static final Logger log = LoggerFactory.getLogger(ElasticSearchExchangeStore.class);

    String index = "membrane";
    ObjectMapper mapper;

    String location = "http://localhost:9200";
    private String documentPrefix;

    final ImmutableMap<String, String> queryToElasticMap = ImmutableMap.ofEntries(
            Map.entry("method", "request.method.keyword"),
            Map.entry("server", "server.keyword" ),
            Map.entry("client", "remoteAddr.keyword" ),
            Map.entry("respcontenttype", "response.header.Content-Type.keyword" ),
            Map.entry("reqcontenttype", "request.header.Content-Type.keyword" ),
            Map.entry("reqcontentlength", "request.header.Content-Length.keyword"),
            Map.entry("respcontentlength", "response.header.Content-Length.keyword"),
            Map.entry("statuscode", "response.statusCode" ),
            Map.entry("path", "request.uri.keyword" ),
            Map.entry("proxy", "rule.name.keyword" )
        );


    @Override
    public void init(Router router) {

        if(client == null)
            client = router.getHttpClientFactory().createClient(null);
        if(mapper == null)
            mapper = new ObjectMapper();

        if(documentPrefix == null)
            documentPrefix = getLocalHostname();
        documentPrefix = documentPrefix.toLowerCase();

        this.setUpIndex();

        super.init(router);
    }

    protected void writeToStore(List<AbstractExchangeSnapshot> exchanges) {
        String data = exchanges
                .stream()
                .map(exchange -> wrapForBulkOperationElasticSearch(index,getLocalMachineNameWithSuffix()+"-"+exchange.getId(),collectExchangeDataFrom(exchange)))
                .collect(joining());

        try {
            Exchange elasticSearchExc = new Request.Builder()
                    .post(location + "/_bulk")
                    .header("Content-Type","application/x-ndjson")
                    .body(data)
                    .buildExchange();

            client.call(elasticSearchExc);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    private static String getLocalHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                return IOUtils.toString(Runtime.getRuntime().exec(new String[]{"hostname"}).getInputStream(), UTF_8);
            } catch (IOException e1) {
                log.error("Unable to get hostname of localhost.", e1);
                return "localhost";
            }
        }
    }

    private String getLocalMachineNameWithSuffix() {
        return documentPrefix + "-" + startTime;
    }

    public String wrapForBulkOperationElasticSearch(String index, String id,String value){
        return "{ \"index\" : { \"_index\" : \"" + index + "\", \"_id\" : \""+id+"\" } }\n" + value + "\n";
    }


    @SuppressWarnings({"unchecked"})
    private String collectExchangeDataFrom(AbstractExchangeSnapshot exc) {
        try {
            Map<String,String> value = mapper.readValue(mapper.writeValueAsString(exc),Map.class);
            value.put("issuer",documentPrefix);
            return mapper.writeValueAsString(value);
        } catch (IOException e) {
            log.error("While collecting data from {}", exc.getRequest().getUri(), e);
            return "";
        }
    }

    private JSONObject getFilterDistinctQueryJson(String field){
        JSONObject query = new JSONObject();
        query.put("size", 0);

        JSONObject aggs = new JSONObject();
        JSONObject langs = new JSONObject();
        JSONObject terms = new JSONObject();

        terms.put("field", field);
        terms.put("size", 500);

        langs.put("terms", terms);
        aggs.put("langs", langs);

        query.put("aggs", aggs);
        return query;
    }


    private List<String> getPropertyValueArray(String propertyName) throws Exception {
        Request.Builder builder = new Request.Builder().post(location  + "/" + "_search")
                .contentType(APPLICATION_JSON);
        Exchange clientExc = builder.body(getFilterDistinctQueryJson(propertyName).toString()).buildExchange();
        clientExc = client.call(clientExc);
        return getDistinctValues(clientExc.getResponse().getBodyAsStringDecoded());
    }

    private List<String>  getDistinctValues(String responseJson){
        return StreamSupport.stream(new JSONObject(responseJson).getJSONObject("aggregations")
                .getJSONObject("langs").getJSONArray("buckets").spliterator(), false)
                .map(q -> ((JSONObject) q).get("key").toString()).collect(Collectors.toList());
    }


    public AbstractExchangeSnapshot getFromStoreById(long id) {
        try {
            String body = """
                    {
                      "query": {
                        "bool": {
                          "must": [
                            {
                              "match": {
                                "issuer": "%s"
                              }
                            },
                            {
                              "match": {
                                "id": "%s"
                              }
                            }
                          ]
                        }
                      }
                    }""".formatted(documentPrefix, id);
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_search")
                    .body(body)
                    .contentType(APPLICATION_JSON)
                    .buildExchange();
            exc = client.call(exc);
            Map excJson = getSourceElementFromElasticSearchResponse(responseToMap(exc)).getFirst();
            return mapper.readValue(mapper.writeValueAsString(excJson),AbstractExchangeSnapshot.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getElasticSearchExchangesPath() {
        return location + "/" + index + "/";
    }

    private String getElasticSearchIndexPath() {
        return location + "/" + index + "/";
    }

    public List<Map> getSourceElementFromElasticSearchResponse(Map response){
        return getSourceElementFromHitsElement(getHitsElementFromElasticSearchResponse(response));
    }

    public List getHitsElementFromElasticSearchResponse(Map response){
        return ((List)((Map<?, ?>)response.get("hits")).get("hits"));
    }

    public List<Map> getSourceElementFromHitsElement(List hits){
        return (List)hits.stream().map(hit -> ((Map<?, ?>)hit).get("_source")).collect(Collectors.toList());
    }

    @Override
    public AbstractExchange getExchangeById(long id) {
        return getFromStoreById(id).toAbstractExchange();
    }

    @Override
    public void remove(AbstractExchange exchange) {
        try {
            removeFromElasticSearchById(exchange.getId());
        } catch (Exception e) {
            log.error("While removing the Exchange.", e);
        }
    }

    private void removeFromElasticSearchById(long id) throws Exception {
        Exchange exc = new Request.Builder()
                .delete(getElasticSearchExchangesPath() + getLocalMachineNameWithSuffix() + "-" + id)
                .buildExchange();
        client.call(exc);
    }

    @Override
    public void removeAllExchanges(Proxy proxy) {
        String name = proxy.toString();
        try {
            String body = """
                    {
                      "query": {
                        "bool": {
                          "must": [
                            {
                              "match": {
                                "issuer": "%s"
                              }
                            },
                            {
                              "match": {
                                "rule.name": "%s"
                              }
                            }
                          ]
                        }
                      }
                    }""".formatted(documentPrefix, name);
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_delete_by_query")
                    .body(body)
                    .contentType(APPLICATION_JSON)
                    .buildExchange();
            client.call(exc);
        } catch (Exception e) {
            log.error("While removing all Exchanges for proxy {}.", proxy.getName(), e);
        }
    }

    @Override
    public void removeAllExchanges(AbstractExchange[] exchanges) {
        var exchangeIdsCommaSeparated = Stream.of(exchanges)
                .map(AbstractExchange::getId)
                .map(Objects::toString)
                .collect(joining(","));

        try {
            String body = """
                    {
                      "query": {
                        "bool": {
                          "must": [
                            {
                              "match": {
                                "issuer": "%s"
                              }
                            },
                            {
                              "terms": {
                                "id": "%s"
                              }
                            }
                          ]
                        }
                      }
                    }""".formatted(documentPrefix, "["+exchangeIdsCommaSeparated+"]");
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_delete_by_query")
                    .body(body)
                    .contentType(APPLICATION_JSON)
                    .buildExchange();
            client.call(exc);
        } catch (Exception e) {
            log.error("While removing all Exchanges.", e);
        }
    }

    @Override
    public AbstractExchange[] getExchanges(RuleKey ruleKey) {
        int port = ruleKey.getPort();
        try {
            String body = """
                    {
                      "query": {
                        "bool": {
                          "must": [
                            {
                              "match": {
                                "issuer": "%s"
                              }
                            },
                            {
                              "match": {
                                "rule.port": "%d"
                              }
                            }
                          ]
                        }
                      }
                    }""".formatted(documentPrefix, port);
            Exchange exc = new Request.Builder()
                    .post(getElasticSearchExchangesPath() + "_search")
                    .body(body)
                    .contentType(APPLICATION_JSON)
                    .buildExchange();
            exc = client.call(exc);

            List<Map> source = getSourceElementFromElasticSearchResponse(responseToMap(exc));
            AbstractExchangeSnapshot[] snapshots = mapper.readValue(mapper.writeValueAsString(source), AbstractExchangeSnapshot[].class);
            return Stream.of(snapshots).map(AbstractExchangeSnapshot::toAbstractExchange).toArray(AbstractExchange[]::new);
        } catch (Exception e) {
            log.error("While retrieving all Exchanges.", e);
            return new AbstractExchange[0];
        }
    }

    @Override
    public Object[] getAllExchanges() {
        return getAllExchangesAsList().toArray();
    }

    @Override
    public List<AbstractExchange> getAllExchangesAsList() {
        try{
            String body = """
                    {
                      "query": {
                        "match": {
                          "issuer": "%s"
                        }
                      }
                    }""".formatted(documentPrefix);
            Exchange exc = new Request.Builder().post(getElasticSearchExchangesPath() + "_search")
                    .contentType(APPLICATION_JSON)
                    .body(body)
                    .buildExchange();
            exc = client.call(exc);

            if(!exc.getResponse().isOk())
                return new ArrayList<>();

            return getAbstractExchangeListFromExchange(exc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<AbstractExchange> getAbstractExchangeListFromExchange(Exchange exc) throws IOException {
        List<Map> sources = getSourceElementFromElasticSearchResponse(responseToMap(exc));

        return sources.stream().map(source -> {
            try {
                return mapper.readValue(mapper.writeValueAsString(source),AbstractExchangeSnapshot.class).toAbstractExchange();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public ExchangeQueryResult getFilteredSortedPaged(QueryParameter params, boolean useXForwardedForAsClientAddr) throws Exception {
        JSONObject req = getJsonElasticQuery(params);

        Exchange exc = new Request.Builder().post(getElasticSearchExchangesPath() + "_search").contentType(APPLICATION_JSON)
                .body(req.toString()).buildExchange();
        exc = client.call(exc);

        return new ExchangeQueryResult(getAbstractExchangeListFromExchange(exc), getTotalHitCountFromExchange(exc),
                this.getLastModified());
    }

    private JSONObject getJsonElasticQuery(QueryParameter params) {
        JSONObject req = new JSONObject();
        req.put("from", params.getString("offset"));
        req.put("size", params.getString("max"));
        JSONObject query = new JSONObject();
        req.put("query", query);
        JSONObject bool = new JSONObject();
        query.put("bool", bool);
        JSONArray must = new JSONArray();
        bool.put("must", must);
        if(!params.getString("sort").equals("duration")) {
            req.put("sort", getSortJSONArray(queryToElasticMap.getOrDefault(params.getString("sort").toLowerCase(), params.getString("sort")),
                    params.getString("order")));
        }
        else{
            req.put("sort",getDurationScriptObject(params.getString("order")));
        }
        List<String> existingFields = queryToElasticMap.keySet().stream().filter(params::has).toList();
        existingFields.forEach( eF -> must.put(getMatchJSON( queryToElasticMap.get(eF), params.getString(eF))));
        must.put(new JSONObject().put("match", new JSONObject().put("issuer", documentPrefix)));

        return req;
    }

    private JSONObject getDurationScriptObject(String sortOrder){
        JSONObject main = new JSONObject();
        JSONObject _script = new JSONObject();
        _script.put("type", "number");

        JSONObject script = new JSONObject();
        script.put("lang", "painless");
        script.put("source", "doc['timeResReceived'].value - doc['timeReqSent'].value");

        _script.put("order", getElasticSortOrder(sortOrder));
        _script.put("script", script);
        main.put("_script", _script);
        return main;
    }

    private JSONObject getMatchJSON(String key, String value){
        return new JSONObject().put("match", new JSONObject().put(key, value));
    }


    private JSONArray getSortJSONArray(String key, String sortOrder){

        sortOrder = getElasticSortOrder(sortOrder);

        return new JSONArray().put(new JSONObject().put(key, sortOrder));
    }

    private String getElasticSortOrder(String sortOrder) {
        sortOrder = sortOrder.equals("asc") ? "asc" : "desc";
        return sortOrder;

    }

    @Override
    public synchronized void collect(ExchangeCollector collector) {
        try {
            ((PropertyValueCollector) collector).getProxies().addAll(getPropertyValueArray("rule.name.keyword"));
            ((PropertyValueCollector) collector).getMethods().addAll(getPropertyValueArray("request.method.keyword"));
            ((PropertyValueCollector) collector).getClients().addAll(getPropertyValueArray("remoteAddr.keyword"));
            ((PropertyValueCollector) collector).getReqContentTypes().addAll(getPropertyValueArray("request.header.Content-Type.keyword"));
            ((PropertyValueCollector) collector).getStatusCodes().addAll(getPropertyValueArray("response.statusCode").stream().map(Integer::parseInt).collect(Collectors.toSet()));
            ((PropertyValueCollector) collector).getRespContentTypes().addAll(getPropertyValueArray("response.header.Content-Type.keyword"));
            ((PropertyValueCollector) collector).getServers().addAll(getPropertyValueArray("server.keyword"));
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private void setUpIndex() {
        try {
            log.info("Setting up elastic search index");
            JSONObject indexRes = new JSONObject(client.call(put(getElasticSearchIndexPath())
                    .body("")
                    .buildExchange()).getResponse().getBodyAsStringDecoded());


            try {
                if( isElasticAcked(indexRes)){
                    log.info("Index {} created",index);
                }
            } catch (JSONException e) {
                if(indexRes.getJSONObject("error").getJSONArray("root_cause")
                        .getJSONObject(0).getString("type").equals("resource_already_exists_exception")){
                    log.info("Index already exists skipping index creation");
                }
                else{
                    log.error("Error happened. Reply from elastic search is below");
                    log.error(indexRes.toString());
                }


            }

            log.info("Setting up elastic search mappings");
            String mapping = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("com.predic8.membrane.core.exchangestore/mapping.json"), UTF_8);

            Exchange currentMappingExc = client.call(new Request.Builder().get(getElasticSearchIndexPath() + "_mapping")
                    .buildExchange());
            String currentMapping = client.call(currentMappingExc).getResponse().getBodyAsStringDecoded();

            if(!new JSONObject(currentMapping).getJSONObject(index).getJSONObject("mappings").isEmpty()){
                log.info("Mapping already set skipping");
                return;
            }

            Exchange exc = client.call(new Request.Builder().put(getElasticSearchIndexPath() + "_mapping").
                            contentType(APPLICATION_JSON).body(mapping).buildExchange());

            JSONObject res = new JSONObject(exc.getResponse().getBodyAsStringDecoded());

            try {
                if (isElasticAcked(res)){
                    log.info("Elastic store mapping update completed");
                }
                // TODO: nothing happens, if this is not acknowledged
            } catch (JSONException e) {
                log.error("There is an error while updating mapping for elastic search. Response from elastic search: {}", res, e);
            }
        } catch (Exception e) {
            log.error("While setting up ElasticSearch Index.", e);
        }
    }

    private boolean isElasticAcked(JSONObject json) {
        return json.getBoolean("acknowledged");
    }

    private Map responseToMap(Exchange exc) throws IOException {
        return mapper.readValue(exc.getResponse().getBodyAsStringDecoded(),Map.class);
    }

    private int getTotalHitCountFromExchange(Exchange exc){
        return new JSONObject(exc.getResponse().getBodyAsStringDecoded()).getJSONObject("hits")
                .getJSONObject("total").getInt("value");
    }

    public HttpClient getClient() {
        return client;
    }

    @MCAttribute
    public void setClient(HttpClient client) {
        this.client = client;
    }

    public String getLocation() {
        return location;
    }

    /**
     * @description base URL of Elasticsearch
     * @default <a href="http://localhost:9200">http://localhost:9200</a>
     */
    @MCAttribute
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * @description index name to use for Elasticsearch
     * @default membrane
     */
    @MCAttribute
    public void setIndex(String index) {
        this.index = index;
    }

    public String getIndex(){
        return this.index;
    }

    public String getDocumentPrefix() {
        return documentPrefix;
    }

    /**
     * @description used for issuer field. Can be used to check which membrane instance is writing current exchange
     * @default set to hostname as default
     */
    @MCAttribute
    public void setDocumentPrefix(String documentPrefix) {
        this.documentPrefix = documentPrefix;
    }

}
