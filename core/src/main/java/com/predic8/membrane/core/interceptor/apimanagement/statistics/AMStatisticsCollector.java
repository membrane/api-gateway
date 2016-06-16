/*
 * Copyright 2015 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.apimanagement.statistics;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@MCElement(name="amStatisticsCollector")
public class AMStatisticsCollector {

    private static Logger log = LogManager.getLogger(AMStatisticsCollector.class);
    public static final String API_STATISTICS_PATH = "/api/statistics/";
    public static final String API_EXCHANGES_PATH = "/api/exchanges/";
    boolean shutdown = false;
    private int collectTimeInSeconds = 10;
    static final String localHostname;
    static final long startTime = System.currentTimeMillis();
    private AtomicInteger runningId = new AtomicInteger(0);
    String host = "localhost";
    private String clientId = null;
    private String clientSecret = null;

    JsonFactory jsonFactory = new JsonFactory();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JsonGenerator jsonGenerator;
    HttpClient client;

    boolean traceStatistics = true;
    boolean traceExchanges = true;
    boolean traceIncludesHeader = true;
    int bodyBytes = -1;

    ConcurrentHashMap<String, ConcurrentLinkedQueue<Exchange>> exchangesForApiKey = new ConcurrentHashMap<String, ConcurrentLinkedQueue<Exchange>>();

    ExecutorService collectorThread = Executors.newFixedThreadPool(1);

    static {
        localHostname = getLocalHostname();
    }

    public AMStatisticsCollector() {
        HttpClientConfiguration conf = new HttpClientConfiguration();
        client =  new HttpClient(conf);
        try {
            jsonGenerator = jsonFactory.createGenerator(baos);
        } catch (IOException ignored) {
        }

        collectorThread.submit(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Exchange exc = null;
                        ArrayList<String> jsonStatisticsForApiKey = new ArrayList<String>();
                        ArrayList<String> jsonExchangesForApiKey = new ArrayList<String>();
                        for (String apiKey : exchangesForApiKey.keySet()) {
                            while ((exc = exchangesForApiKey.get(apiKey).poll()) != null) {
                                String exchangeStatistics = null;
                                String exchangeData = null;
                                if (traceStatistics) {
                                    try {
                                        exchangeStatistics = collectStatisticFrom(exc, apiKey);
                                        jsonStatisticsForApiKey.add(exchangeStatistics);
                                    } catch (Exception ignored) {
                                        continue;
                                    }

                                }
                                if (traceExchanges) {
                                    try {
                                        exchangeData = collectExchangeDataFrom(exc, apiKey);
                                        jsonExchangesForApiKey.add(exchangeData);
                                    } catch (Exception ignored) {
                                        continue;
                                    }
                                }
                            }
                        }
                        if (!jsonStatisticsForApiKey.isEmpty())
                            sendJsonToElasticSearch(API_STATISTICS_PATH, combineJsons(localHostname, jsonStatisticsForApiKey));
                        if (!jsonExchangesForApiKey.isEmpty())
                            sendJsonToElasticSearch(API_EXCHANGES_PATH, combineJsons(localHostname, jsonExchangesForApiKey));
                        runningId.incrementAndGet();
                        if (shutdown)
                            break;
                        Thread.sleep(getCollectTimeInSeconds() * 1000);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private String collectExchangeDataFrom(Exchange exc, String apiKey) throws IOException {
        JsonGenerator gen = getAndResetJsonGenerator();

        try {
            gen.writeStartObject();
            gen.writeObjectField("excId", exc.getId());
            gen.writeObjectField("excApiKey", apiKey);
            gen.writeObjectField("service", exc.getRule().getName());
            gen.writeObjectField("uri", exc.getOriginalRequestUri());
            gen.writeObjectField("method", exc.getRequest().getMethod());
            gen.writeObjectFieldStart("Request");
            collectFromMessage(gen, exc.getRequest());
            gen.writeEndObject();
            gen.writeObjectFieldStart("Response");
            collectFromMessage(gen, exc.getResponse());
            gen.writeEndObject();
            gen.writeEndObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getStringFromJsonGenerator();
    }

    private void collectFromMessage(JsonGenerator gen, Message msg) {
        try {
            if (traceIncludesHeader) {
                if (msg.getHeader().getAllHeaderFields().length > 0) {
                    gen.writeObjectFieldStart("headers");
                    for (HeaderField hf : msg.getHeader().getAllHeaderFields()) {
                        gen.writeObjectField(hf.getHeaderName().toString(), hf.getValue());
                    }
                    gen.writeEndObject();
                }
            }
            String body = getBody(msg);
            if (body.length() > 0)
                gen.writeObjectField("body", body);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getBody(Message msg) {
        String origBody = msg.getBodyAsStringDecoded();
        if (bodyBytes == -1)
            return origBody.substring(0, origBody.length());

        return origBody.substring(0, bodyBytes);
    }


    private String getLocalMachineNameWithSuffix() {
        return localHostname + "-" + startTime + "-" + runningId.get();
    }

    private void sendJsonToElasticSearch(String path, String json) throws Exception {
        Response resp = null;
        synchronized (client) {
            Exchange exc = new Request.Builder().put(getElasticSearchPath(path))
                    .body(json)
                    .buildExchange();

            if(clientId != null && clientSecret != null)
                exc.getRequest().getHeader().add(Header.AUTHORIZATION, "Basic " + new String(Base64.encodeBase64((clientId + ":" + clientSecret).getBytes("UTF-8")), "UTF-8"));

            resp = client.call(exc).getResponse();
        }
        if (!resp.isOk())
            log.warn("Could not send statistics to elastic search instance. Response: " + resp.getStatusCode() + " - " + resp.getStatusMessage() + " - " + resp.getBodyAsStringDecoded());
    }

    private String combineJsons(String name, ArrayList<String> jsonStatisticsForRequests) throws IOException {
        JsonGenerator gen = getAndResetJsonGenerator();

        try {
            gen.writeStartObject();
            gen.writeArrayFieldStart(name);
            if (!jsonStatisticsForRequests.isEmpty())
                gen.writeRaw(jsonStatisticsForRequests.get(0));
            for (int i = 1; i < jsonStatisticsForRequests.size(); i++) {
                gen.writeRaw("," + jsonStatisticsForRequests.get(i));
            }
            gen.writeEndArray();
            gen.writeEndObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getStringFromJsonGenerator();
    }

    private String collectStatisticFrom(Exchange exc, String apiKey) throws IOException {
        StatisticCollector statistics = new StatisticCollector(false);
        statistics.collectFrom(exc);

        JsonGenerator gen = getAndResetJsonGenerator();


        try {
            gen.writeStartObject();
            gen.writeObjectField("excId", exc.getId());
            gen.writeObjectField("excApiKey", apiKey);
            gen.writeObjectField("service", exc.getRule().getName());
            gen.writeObjectField("uri", exc.getOriginalRequestUri());
            gen.writeObjectField("method", exc.getRequest().getMethod());
            gen.writeObjectField("excStatus", exc.getStatus().toString());
            gen.writeObjectField("code", exc.getResponse().getStatusCode());
            gen.writeObjectField("time", getInflightTime(exc));
            gen.writeEndObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getStringFromJsonGenerator();
    }

    private long getInflightTime(Exchange exc) {
        if (exc.getTimeReqSent() == 0)
            return -1;
        else
            return exc.getTimeResSent() - exc.getTimeReqSent();
    }

    private String getElasticSearchPath(String path) {
//        if(host.equals("localhost"))
//            return "http://" + getHost() + ":" + elasticSearchPort + normalizePath(path) + getLocalMachineNameWithSuffix();
        return getHost() + normalizePath(path) + getLocalMachineNameWithSuffix();
    }

    private String normalizePath(String path) {
        if (!path.startsWith("/"))
            path = "/" + path;
        if (!path.endsWith("/"))
            path = path + "/";
        return path;
    }

    /**
     * Inits listener and puts exchange in a queue for later processing
     *
     * @param exc
     * @param outcome
     * @return always returns the outcome unmodified
     */
    public Outcome handleRequest(final Exchange exc, final Outcome outcome) {
        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void setExchangeFinished() {
                addExchangeToQueue(exc);
            }
        });

        /* Ask Tobias if this is a better alternative than to wait for body complete
         exc.getRequest().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addRequestBody(exc, bodyBytes);
            }
        });*/


        return outcome;
    }

    public void addExchangeToQueue(Exchange exc) {
        String apiKey = (String) exc.getProperty(Exchange.API_KEY);

        if (apiKey != null) {
            ConcurrentLinkedQueue<Exchange> exchangeQueue = exchangesForApiKey.get(apiKey);

            // See SO 3752194 for explanation for this
            if (exchangeQueue == null) {
                ConcurrentLinkedQueue<Exchange> newValue = new ConcurrentLinkedQueue<Exchange>();
                exchangeQueue = exchangesForApiKey.putIfAbsent(apiKey, newValue);
                if (exchangeQueue == null)
                    exchangeQueue = newValue;
            }

            exchangeQueue.add(exc);
        }
    }

    public Outcome handleResponse(Exchange exc, Outcome outcome) {
        /* Ask Tobias if this is a better alternative than to wait for body complete
        exc.getResponse().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addResponseBody(exc,bodyBytes);
            }
        });*/

        return outcome;
    }

    public int getCollectTimeInSeconds() {
        return collectTimeInSeconds;
    }

    public void setCollectTimeInSeconds(int collectTimeInSeconds) {
        this.collectTimeInSeconds = collectTimeInSeconds;
    }

    public void shutdown() {
        shutdown = true;
        try {
            collectorThread.shutdown();
            collectorThread.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

    protected JsonGenerator getAndResetJsonGenerator(){
        baos.reset();
        return jsonGenerator;
    }

    protected String getStringFromJsonGenerator() throws IOException {
        jsonGenerator.flush();
        return baos.toString();
    }


    public String getHost() {
        return host;
    }

    @MCAttribute
    public void setHost(String host) {
        this.host = host;
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
}
