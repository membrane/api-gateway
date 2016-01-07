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
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.StatisticCollector;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ApiKeyStatistic {

    private static Logger log = LogManager.getLogger(ApiKeyStatistic.class);

    String apiKey;

    int bodyBytesToLog = -1;
    String elasticSearchHost = "localhost";
    int elasticSearchPort = 9200;
    HttpClient client = new HttpClient();
    JsonFactory jsonFactory = new JsonFactory();
    private AtomicInteger runningId = new AtomicInteger(0);


    static final int defaultId = 1;
    static final String statisticsType = "statistics";
    static final String localHostname;
    static final long startTime = System.currentTimeMillis();

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

    /**
     * The key is the name of the service
     */
    ConcurrentHashMap<String, ServiceStatistic> statisticsForService = new ConcurrentHashMap<String, ServiceStatistic>();

    public ApiKeyStatistic(String apiKey){
        this(apiKey,-1);
    }

    public ApiKeyStatistic(String apiKey, int bodyBytes) {
        this.apiKey = apiKey;
        this.bodyBytesToLog = bodyBytes;
    }

    public void collectFrom(Exchange exc) {
        StatisticCollector collector = new StatisticCollector(false);
        collector.collectFrom(exc);
        String host = ((ServiceProxy)exc.getRule()).getTargetHost();
        String path = exc.getRequestURI();



        String serviceName = exc.getRule().getName();
        ServiceStatistic serviceSta = statisticsForService.get(serviceName);
        if(serviceSta == null){
            ServiceStatistic newValue = new ServiceStatistic(serviceName);
            serviceSta = statisticsForService.putIfAbsent(serviceName,newValue);
            if(serviceSta == null)
                serviceSta = newValue;
        }
        PathStatistic pathSta = serviceSta.getStatisticsForPath().get(path);
        if(pathSta == null){
            PathStatistic newValue = new PathStatistic(path);
            pathSta = serviceSta.getStatisticsForPath().putIfAbsent(path,newValue);
            if(pathSta == null)
                pathSta = newValue;
        }
        synchronized(pathSta) {
            pathSta.getStatistics().collectFrom(collector);
        }

        writeToElasticSearch(host,path,createJsonStatistics(pathSta.getStatistics()));

    }

    private String createJsonStatistics(StatisticCollector statistics) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        JsonGenerator gen = createJsonGenerator(os);
        try {
            gen.writeStartObject();
            gen.writeObjectField("min" ,statistics.getMinTime());
            gen.writeObjectField("max" ,statistics.getMaxTime());
            gen.writeObjectField("avg" ,statistics.getAvgTime());
            gen.writeObjectField("total" ,statistics.getCount());
            gen.writeObjectField("error" ,statistics.getErrorCount());
            gen.writeEndObject();
            gen.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return os.toString();
    }

    private JsonGenerator createJsonGenerator(ByteArrayOutputStream os) {
        synchronized(jsonFactory){
            try {
                return jsonFactory.createGenerator(os);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void writeToElasticSearch(String host, String path, String json) {
        String elasticSearchUrl = getElasticSearchPath(host,path);
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

    private String getElasticSearchPath(String host, String path) {
        return "http://" + elasticSearchHost + ":" + elasticSearchPort + "/api/statistics/" + getLocalHostID() + "-" + startTime + "-" + runningId.getAndIncrement();
    }

    public void addRequestBody(Exchange exc, int bodyBytes) {
    }

    public void addResponseBody(Exchange exc, int bodyBytes) {
    }

    public String getLocalHostID() {
        return localHostname;
    }
}
