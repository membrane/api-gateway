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
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AMStatisticsCollector {

    int collectTimeInSeconds = 1;
    static final String localHostname;
    static final long startTime = System.currentTimeMillis();
    private AtomicInteger runningId = new AtomicInteger(0);
    String elasticSearchHost = "localhost";
    int elasticSearchPort = 9200;

    JsonFactory jsonFactory = new JsonFactory();
    HttpClient client = new HttpClient();

    static{
        String localHostname1 = null;
        try {
            localHostname1 = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            try {
                localHostname1 = IOUtils.toString(Runtime.getRuntime().exec("hostname").getInputStream());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        localHostname = localHostname1;
    }

    boolean traceExchanges;
    boolean traceIncludesHeader;
    int bodyBytes = -1;

    ConcurrentHashMap<String,ConcurrentLinkedQueue<Exchange>> exchangesForApiKey = new ConcurrentHashMap<String,ConcurrentLinkedQueue<Exchange>>();

    ExecutorService collectorThread = Executors.newFixedThreadPool(1);

    public AMStatisticsCollector(){
        collectorThread.submit(new Runnable() {
            @Override
            public void run() {
                while(true) {
                    try {
                        Exchange exc = null;
                        ArrayList<String> jsonStatisticsForApiKey = new ArrayList<String>();
                        for(String apiKey : exchangesForApiKey.keySet()){
                            ArrayList<String> jsonStatisticsForRequests = new ArrayList<String>();
                            while((exc = exchangesForApiKey.get(apiKey).poll()) != null) {
                                jsonStatisticsForRequests.add(collectFrom(exc));
                            }
                            if(!jsonStatisticsForRequests.isEmpty()) {
                                String e = combineJsons(apiKey, jsonStatisticsForRequests);
                                //System.out.println(e);
                                jsonStatisticsForApiKey.add(e);
                            }
                        }
                        if(!jsonStatisticsForApiKey.isEmpty())
                            sendJsonToElasticSearch("/api/statistics/",combineJsons(localHostname, jsonStatisticsForApiKey));

                        Thread.sleep(collectTimeInSeconds * 1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private String getLocalMachineNameWithSuffix() {
        return localHostname + "-" + startTime + "-" + runningId.getAndIncrement();
    }

    private void sendJsonToElasticSearch(String path, String json) {
        String elasticSearchUrl = getElasticSearchPath(path);
        System.out.println(elasticSearchUrl);
        Exchange exc = null;
        try {
            exc = new Request.Builder().put(elasticSearchUrl).body(json).buildExchange();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        Response resp = null;
        synchronized(client){
            try {
                resp = client.call(exc).getResponse();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
        if(!resp.isOk()){
            System.out.println("code not 2xx");
            System.out.println(resp.getBodyAsStringDecoded());
            throw new RuntimeException();
        }
    }

    private String combineJsons(String name, ArrayList<String> jsonStatisticsForRequests) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator gen = createJsonGenerator(os);

        try {
            gen.writeStartObject();
            gen.writeArrayFieldStart(name);
            if(!jsonStatisticsForRequests.isEmpty())
                gen.writeRaw(jsonStatisticsForRequests.get(0));
            for(int i = 1; i < jsonStatisticsForRequests.size();i++){
                gen.writeRaw("," + jsonStatisticsForRequests.get(i));
            }
            gen.writeEndArray();
            gen.writeEndObject();
            gen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os.toString();
    }

    private String collectFrom(Exchange exc) {
        StatisticCollector statistics = new StatisticCollector(false);
        statistics.collectFrom(exc);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator gen = createJsonGenerator(os);


        try {
            gen.writeStartObject();
            gen.writeObjectField("excId", exc.getId());
            gen.writeObjectField("excStatus", exc.getStatus().toString());
            gen.writeObjectField("code" ,exc.getResponse().getStatusCode());
            int time = 0;
            if(exc.getTimeReqSent() == 0)
                time = -1;
            else
                time = (int) (exc.getTimeResSent() - exc.getTimeReqSent());
            gen.writeObjectField("time", time);
            gen.writeEndObject();
            gen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os.toString();
    }

    private String getElasticSearchPath(String path) {
        if(!path.startsWith("/"))
            path = "/" + path;
        if(!path.endsWith("/"))
            path = path + "/";
        return "http://" + elasticSearchHost + ":" + elasticSearchPort + path + getLocalMachineNameWithSuffix();
    }

    /**
     * Inits listeners and collects statistik from the exchange
     * @param exc
     * @param outcome
     * @return always returns the outcome unmodified
     */
    public Outcome handleRequest(final Exchange exc, final Outcome outcome){
        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {
            @Override
            public void setExchangeFinished() {
                String apiKey = (String) exc.getProperty(Exchange.API_KEY);


                ConcurrentLinkedQueue<Exchange> exchangeQueue = exchangesForApiKey.get(apiKey);

                // See SO 3752194 for explanation for this
                if(exchangeQueue == null){
                    ConcurrentLinkedQueue<Exchange> newValue = new ConcurrentLinkedQueue<Exchange>();
                    exchangeQueue = exchangesForApiKey.putIfAbsent(apiKey,newValue);
                    if(exchangeQueue == null)
                        exchangeQueue = newValue;
                }

                exchangeQueue.add(exc);
            }
        });

        /*exc.getRequest().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addRequestBody(exc, bodyBytes);
            }
        });*/



        return outcome;
    }

    public Outcome handleResponse(Exchange exc, Outcome outcome) {
        /*exc.getResponse().addObserver(new AbstractMessageObserver() {
            @Override
            public void bodyComplete(AbstractBody body) {
                //statistics.addResponseBody(exc,bodyBytes);
            }
        });*/

        return outcome;
    }

    private JsonGenerator createJsonGenerator(ByteArrayOutputStream os) {
        synchronized(jsonFactory){
            try {
                return jsonFactory.createGenerator(os);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
    }
}
