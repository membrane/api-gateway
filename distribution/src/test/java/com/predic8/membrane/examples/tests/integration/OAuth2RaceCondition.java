/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
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

package com.predic8.membrane.examples.tests.integration;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OAuth2RaceCondition {


    private Router server;
    private Router client;

    @Before
    public void setUp() throws MalformedURLException {
        server = HttpRouter.init(System.getProperty("user.dir") + "\\src\\test\\resources\\OAuth2\\server.xml");
        client = HttpRouter.init(System.getProperty("user.dir") + "\\src\\test\\resources\\OAuth2\\client.xml");
    }

    @After
    public void tearDown() throws MalformedURLException {
        server.stop();
        client.stopAll();
    }

    @Test
    public void testSessionIdStateRaceCondition() throws Exception {
        HttpClient hc = HttpClientBuilder.create().build();

        login(hc);
        System.out.println("Logged in");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 1; i++) {

//            HttpClient hc1 = HttpClientBuilder.create().build();
//            login(hc1);
            Future<Exception>[] results = new Future[2];

            int parallelReqs = 2;
            CountDownLatch cdl = new CountDownLatch(parallelReqs);

            for (int j = 0; j < parallelReqs; j++) {
                final int fj = j;
                results[j] = executor.submit(() -> {
                    try {
                        int uri = (fj %2 == 0 ? 1 : 2);
                        HttpGet get = new HttpGet("http://localhost:2001/test" + uri);
                        //setNoRedirects(get);
                        cdl.countDown();
                        cdl.await();
                        try(CloseableHttpResponse getRes = (CloseableHttpResponse) hc.execute(get)) {
                            assertEquals(200, getRes.getStatusLine().getStatusCode());
                            String resText = EntityUtils.toString(getRes.getEntity(),"UTF-8");
                            System.out.println("Called: Test" + uri + ".\nActual: " + resText);
                            assertTrue(resText.contains(Integer.toString(uri)));
                        }
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                });
            }
            for (int j = 0; j < parallelReqs; j++) {
                results[j].get();
            }

            for (int j = 0; j < parallelReqs; j++) {
                Exception e = results[j].get();
                if (e != null)
                    throw new RuntimeException(e);
            }

        }
        executor.shutdown();
    }

    private void login(HttpClient client) throws IOException {
        HttpGet clientGet = new HttpGet("http://localhost:2001");
        try(CloseableHttpResponse clientGetRes = (CloseableHttpResponse) client.execute(clientGet)) {
            assertEquals(200, clientGetRes.getStatusLine().getStatusCode());
        }

        HttpPost loginPost = new HttpPost("http://localhost:2000/login/");
        loginPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
        loginPost.setEntity(new UrlEncodedFormEntity(Lists.newArrayList(
                new BasicNameValuePair("username", "john"),
                new BasicNameValuePair("password", "password")
        )));
        try(CloseableHttpResponse loginPostRes = (CloseableHttpResponse) client.execute(loginPost)) {
            assertEquals(200, loginPostRes.getStatusLine().getStatusCode());
        }

        HttpGet followGet = new HttpGet("http://localhost:2000/");
        try(CloseableHttpResponse followGetRes = (CloseableHttpResponse) client.execute(followGet)) {
            assertEquals(200, followGetRes.getStatusLine().getStatusCode());
        }
        this.client.stopAll();
        this.client = HttpRouter.init(System.getProperty("user.dir") + "\\src\\test\\resources\\OAuth2\\client.xml");
    }

    private void setNoRedirects(HttpRequestBase get) {
        BasicHttpParams params = new BasicHttpParams();
        params.setParameter(ClientPNames.HANDLE_REDIRECTS, false);
        get.setParams(params);
    }
}
