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

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.test.AssertUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED_CONTENT_TYPE;
import static com.predic8.membrane.test.AssertUtils.getAndAssert;
import static com.predic8.membrane.test.AssertUtils.getAndAssert200;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OAuth2Test {

    public static Collection<Object[]> data() {
        return asList(testParams(), testParamsForSubPath());
    }

    private static Object[] testParams() {
        return new Object[] { "proxies.xml", "", ""};
    }

    private static Object[] testParamsForSubPath() {
        return new Object[] { "proxies-subpath.xml", "/server", "/client" };
    }

    private Router router;

    public void setUp(String proxies) throws Exception {
        if (router != null)
            return;
        router = HttpRouter.init(getBasePath() + "/src/test/resources/OAuth2/" + proxies);
    }

    private String getBasePath() {
        String basePath = System.getProperty("user.dir");
        return basePath.startsWith("/") ? "file://" + basePath : basePath;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testGoodLoginRequest(
            String proxies,
            String serverBasePath,
            String clientBasePath) throws Exception {
        setUp(proxies);

        getAndAssert200("http://localhost:2001" + clientBasePath);
        AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", getWWWFormEncodedContentTypeHeader(), "target=&username=john&password=password");
        assertEquals("Hello john.", getAndAssert200("http://localhost:2000" + serverBasePath + "/"));
    }

    private String[] getWWWFormEncodedContentTypeHeader() {
        String[] headers = new String[2];
        headers[0] = "Content-Type";
        headers[1] = APPLICATION_X_WWW_FORM_URLENCODED;
        return headers;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testBadUserCredentials(
            String proxies,
            String serverBasePath,
            String clientBasePath) throws Exception {
        setUp(proxies);

        getAndAssert200("http://localhost:2001" + clientBasePath);
        assertTrue(AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", getWWWFormEncodedContentTypeHeader(), "target=&username=john&password=wrongPassword").contains("Invalid password."));
        getAndAssert(400, "http://localhost:2000" + serverBasePath + "/");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testMissingHeader(
            String proxies,
            String serverBasePath,
            String clientBasePath) throws Exception {
        setUp(proxies);

        getAndAssert200("http://localhost:2001" + clientBasePath);
        assertTrue(AssertUtils.postAndAssert(200, "http://localhost:2000" + serverBasePath + "/login/", "target=&username=john&password=password").contains("Invalid password."));
        getAndAssert(400, "http://localhost:2000" + serverBasePath + "/");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testBypassingAuthorizationService(
            String proxies,
            String serverBasePath,
            String clientBasePath) throws Exception {
        setUp(proxies);

        getAndAssert(400, "http://localhost:2000" + serverBasePath + "/oauth2/auth");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testLogout(
            String proxies,
            String serverBasePath,
            String clientBasePath) throws Exception {
        setUp(proxies);

        testGoodLoginRequest(proxies, serverBasePath, clientBasePath);
        getAndAssert200("http://localhost:2001" + clientBasePath + "/login/logout");
    }

    @AfterEach
    public void tearDown() {
        router.stopAll();
        router = null;
    }
}
