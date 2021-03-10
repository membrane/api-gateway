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
import com.predic8.membrane.test.AssertUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class OAuth2Test {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() throws Exception {
        return Arrays.asList(new Object[][] {
                testParams(),
                testParamsForSubPath()
        });
    }

    private static Object[] testParams() {
        return new Object[] { "proxies.xml", "", ""};
    }

    private static Object[] testParamsForSubPath() {
        return new Object[] { "proxies-subpath.xml", "/server", "/client" };
    }

    @Parameterized.Parameter(value = 0)
    public String proxies;
    @Parameterized.Parameter(value = 1)
    public String serverBasePath;
    @Parameterized.Parameter(value = 2)
    public String clientBasePath;

    private Router router;

    @Before
    public void setUp() throws Exception {
        router = HttpRouter.init(getBasePath() + "/src/test/resources/OAuth2/" + proxies);
    }

    private String getBasePath() {
        String basePath = System.getProperty("user.dir");
        return basePath.startsWith("/") ? "file://" + basePath : basePath;
    }

    @Test
    public void testGoodLoginRequest() throws Exception {
        AssertUtils.getAndAssert200("http://localhost:2001" + clientBasePath);
        String[] headers = new String[2];
        headers[0] = "Content-Type";
        headers[1] = "application/x-www-form-urlencoded";
        AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", headers, "target=&username=john&password=password");
        assertEquals("Hello john.", AssertUtils.getAndAssert200("http://localhost:2000" + serverBasePath + "/"));
    }

    @Test
    public void testBadUserCredentials() throws Exception {
        AssertUtils.getAndAssert200("http://localhost:2001" + clientBasePath);
        String[] headers = new String[2];
        headers[0] = "Content-Type";
        headers[1] = "application/x-www-form-urlencoded";
        assertEquals(true, AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", headers, "target=&username=john&password=wrongPassword").contains("Invalid password."));
        AssertUtils.getAndAssert(400, "http://localhost:2000" + serverBasePath + "/");
    }

    @Test
    public void testMissingHeader() throws Exception {
        AssertUtils.getAndAssert200("http://localhost:2001" + clientBasePath);
        assertEquals(true, AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", "target=&username=john&password=password").contains("Invalid password."));
        AssertUtils.getAndAssert(400, "http://localhost:2000" + serverBasePath + "/");
    }

    @Test
    public void testBypassingAuthorizationService() throws Exception {
        AssertUtils.getAndAssert(400, "http://localhost:2000" + serverBasePath + "/oauth2/auth");
    }

    @Test
    public void testLogout() throws Exception {
        testGoodLoginRequest();
        AssertUtils.getAndAssert200("http://localhost:2001" + clientBasePath + "/login/logout");
    }

    @After
    public void tearDown() throws Exception {
        router.stopAll();
    }
}
