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

import com.google.common.collect.Lists;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.HttpClient;
import com.predic8.membrane.core.transport.http.HttpClientFactory;
import com.predic8.membrane.core.transport.http.client.HttpClientConfiguration;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.predic8.membrane.annot.Constants.PRODUCT_NAME;
import static com.predic8.membrane.core.http.Header.USER_AGENT;
import static com.predic8.membrane.core.http.Request.Builder;
import static com.predic8.membrane.core.http.Request.METHOD_GET;
import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.UTF_8;

@MCElement(name = "httpSchemaResolver")
public class HTTPSchemaResolver implements SchemaResolver {

    private HttpClientFactory httpClientFactory;
    private final ConcurrentHashMap<String, byte[]> watchedUrlMd5s = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExceptionThrowingConsumer<InputStream>> consumerForUrls = new ConcurrentHashMap<>();
    private final int httpWatchIntervalInSeconds = 1;
    Thread httpWatcher = null;

    private static final byte[] NO_HASH = "NO_HASH".getBytes(UTF_8);

    private final HttpClient httpClient;
    private final URIFactory uriFactory = new URIFactory(false);

    private final Runnable httpWatchJob = new Runnable() {
        @Override
        public void run() {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            try (HttpClient client = httpClientFactory.createClient(null)) {
                while (!watchedUrlMd5s.isEmpty()) {
                    try {
                        for (String url : watchedUrlMd5s.keySet()) {
                            md.reset();
                            var resolveExchange = createResolveExchange(url);
                            client.call(resolveExchange);
                            Response response = resolveExchange.getResponse();
                            if (response.getStatusCode() != 200) {
                                throw new ResourceRetrievalException(url, response.getStatusCode());
                            }
                            byte[] hash = md.digest(response.getBody().getContent());
                            if (Arrays.equals(watchedUrlMd5s.get(url), NO_HASH)) {
                                watchedUrlMd5s.put(url, hash);
                            } else {
                                if (!Arrays.equals(hash, watchedUrlMd5s.get(url))) {
                                    ExceptionThrowingConsumer<InputStream> inputStreamConsumer = consumerForUrls.get(url);
                                    watchedUrlMd5s.remove(url);
                                    consumerForUrls.remove(url);
                                    inputStreamConsumer.accept(response.getBodyAsStream());
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        //noinspection BusyWait
                        sleep(httpWatchIntervalInSeconds * 1000L);
                    } catch (InterruptedException ignored) {
                    }
                }
                httpWatcher = null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };

    private Exchange createResolveExchange(String url) throws URISyntaxException {
        return new Builder().method(METHOD_GET).url(uriFactory, url).header(USER_AGENT, PRODUCT_NAME).buildExchange();
    }

    public HTTPSchemaResolver(@Nullable HttpClient httpClient, HttpClientFactory httpClientFactory) {
        if(httpClient == null) this.httpClient = httpClientFactory.createClient(new HttpClientConfiguration());
        else this.httpClient = httpClient;
    }

    @Override
    public List<String> getSchemas() {
        return Lists.newArrayList("http", "https");
    }

    public InputStream resolve(String url) throws ResourceRetrievalException {
        try {
            var resolveExchange = createResolveExchange(url);
            httpClient.call(resolveExchange);
            Response response = resolveExchange.getResponse();
            response.readBody();

            if (response.getStatusCode() != 200) {
                throw new ResourceRetrievalException(url, response.getStatusCode());
            }
            return new ByteArrayInputStream(response.getBodyAsStreamDecoded().readAllBytes());
        } catch (ResourceRetrievalException e) {
            throw e;
        } catch (Exception e) {
            throw new ResourceRetrievalException(url, e);
        }
    }


    @Override
    public void observeChange(String url, ExceptionThrowingConsumer<InputStream> consumer) {
        watchedUrlMd5s.put(url, NO_HASH);
        consumerForUrls.put(url, consumer);
        if (httpWatcher == null) {
            httpWatcher = new Thread(httpWatchJob);
        }
        if (!httpWatcher.isAlive()) {
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

}
