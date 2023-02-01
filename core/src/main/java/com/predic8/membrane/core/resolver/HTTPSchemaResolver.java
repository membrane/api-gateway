/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.resolver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.TimerManager;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.functionalInterfaces.Consumer;

import javax.annotation.Nullable;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static java.lang.Thread.sleep;

@MCElement(name = "httpSchemaResolver")
public class HTTPSchemaResolver implements SchemaResolver {

    private HttpClientFactory httpClientFactory;
    private ConcurrentHashMap<String,String> watchedUrlMd5s = new ConcurrentHashMap<String,String>();
    private ConcurrentHashMap<String,Consumer<InputStream>> consumerForUrls = new ConcurrentHashMap<String, Consumer<InputStream>>();
    int httpWatchIntervalInSeconds = 1;
    Thread httpWatcher = null;
    Runnable httpWatchJob = new Runnable() {
        @Override
        public void run() {
            MessageDigest md5 = null;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ignored) {
            }
            HttpClient client = httpClientFactory.createClient(null);
            while (watchedUrlMd5s.size() > 0) {
                try {
                    for (String url : watchedUrlMd5s.keySet()) {
                        md5.reset();
                        Exchange exc = new Builder().method(METHOD_GET).url(uriFactory, url).header(USER_AGENT, PRODUCT_NAME + " " + VERSION).buildExchange();
                        Response response = client.call(exc).getResponse();
                        if (response.getStatusCode() != 200) {
                            ResourceRetrievalException rde = new ResourceRetrievalException(url, response.getStatusCode());
                            throw rde;
                        }
                        String hash = new String(md5.digest(response.getBody().getContent()));
                        if (watchedUrlMd5s.get(url).equals("")) {
                            watchedUrlMd5s.put(url, hash);
                        } else {
                            if (!hash.equals(watchedUrlMd5s.get(url))) {
                                Consumer<InputStream> inputStreamConsumer = consumerForUrls.get(url);
                                watchedUrlMd5s.remove(url);
                                consumerForUrls.remove(url);
                                inputStreamConsumer.call(response.getBodyAsStream());
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    sleep(httpWatchIntervalInSeconds * 1000L);
                } catch (InterruptedException ignored) {
                }
            }
            httpWatcher = null;
        }
    };


    private HttpClientConfiguration httpClientConfig = new HttpClientConfiguration();

    private HttpClient httpClient;
    private URIFactory uriFactory = new URIFactory(false);

    public HTTPSchemaResolver(@Nullable HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
    }

    private synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            if (httpClientFactory == null)
                httpClientFactory = new HttpClientFactory(null);
            httpClient = httpClientFactory.createClient(httpClientConfig);
        }
        return httpClient;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("http", "https");
    }

    public InputStream resolve(String url) throws ResourceRetrievalException {
        try {
            Exchange exc = new Builder().method(METHOD_GET).url(uriFactory, url).header(USER_AGENT, PRODUCT_NAME + " " + VERSION).buildExchange();
            Response response = getHttpClient().call(exc).getResponse();
            response.readBody();

            if (response.getStatusCode() != 200) {
                ResourceRetrievalException rde = new ResourceRetrievalException(url, response.getStatusCode());
                throw rde;
            }
            return new ByteArrayInputStream(ByteUtil.getByteArrayData(response.getBodyAsStreamDecoded()));
        } catch (ResourceRetrievalException e) {
            throw e;
        } catch (Exception e) {
            ResourceRetrievalException rre = new ResourceRetrievalException(url, e);
            throw rre;
        }
    }

    @Override
    public void observeChange(String url, Consumer<InputStream> consumer) throws ResourceRetrievalException {
        watchedUrlMd5s.put(url,"");
        consumerForUrls.put(url,consumer);
        if(httpWatcher == null){
            httpWatcher = new Thread(httpWatchJob);
        }
        if(!httpWatcher.isAlive()){
            httpWatcher.start();
        }
    }

    @Override
    public List<String> getChildren(String url) {
        return null;
    }

    @Override
    public long getTimestamp(String url) {
        return 0;
    }

    public synchronized HttpClientConfiguration getHttpClientConfig() {
        return httpClientConfig;
    }

    public synchronized void setHttpClientConfig(HttpClientConfiguration httpClientConfig) {
        this.httpClientConfig = httpClientConfig;
        httpClient = null;
    }
}
