/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.rest.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.regex.*;

import static com.predic8.membrane.core.exchangestore.AbstractExchangeStore.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static org.junit.jupiter.api.Assertions.*;

public class AbstractExchangeStoreTest {

    private static final Pattern pattern = Pattern.compile("/.*(/?\\?.*)?");

    @SuppressWarnings("FieldCanBeLocal")
    private Exchange e1, e2, e3, e4, e5;
    private final LimitedMemoryExchangeStore exchangeStore = new LimitedMemoryExchangeStore();


    @BeforeEach
    void setUp() throws URISyntaxException {
        e1 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo","Bar").buildExchange();
        e1.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Foo","XXX ZZZ AAA").build());
        e1.setOriginalRequestUri("/foo/bar");
        e1.setRule(new APIProxy());

        e2 = Request.post("/products").contentType(APPLICATION_JSON).header("Foo Goo","Bar").buildExchange();
        e2.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Goo","XXX ZZZ AAA").build());
        e2.setOriginalRequestUri("/poo/bar");
        e2.setRule(new APIProxy());

        e3 = Request.post("/products").contentType(APPLICATION_JSON).header("Zoo Goo","Bar").buildExchange();
        e3.setResponse(Response.badRequest().contentType(TEXT_HTML_UTF8).header("X-OO","XXX ZZZ AAA").build());
        e3.setOriginalRequestUri("/bar");
        e3.setRule(new APIProxy());

        e4 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo Goo","Bar").buildExchange();
        e4.setResponse(Response.badRequest().contentType(TEXT_HTML_UTF8).header("X-Bar","XXX ZZZ AAA").build());
        e4.setOriginalRequestUri("/foo/bar");
        e4.setRule(new APIProxy());

        e5 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo Goo","Bar").buildExchange();
        e5.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Goo","XXX ZZZ AAA").build());
        e5.setOriginalRequestUri("/goo/bar");
        e5.setRule(new APIProxy());

        exchangeStore.snap(e1, REQUEST);
        exchangeStore.snap(e1, RESPONSE);
        exchangeStore.snap(e2, REQUEST);
        exchangeStore.snap(e2, RESPONSE);
        exchangeStore.snap(e3, REQUEST);
        exchangeStore.snap(e3, RESPONSE);
        exchangeStore.snap(e3, REQUEST);
        exchangeStore.snap(e3, RESPONSE);
        exchangeStore.snap(e4, REQUEST);
        exchangeStore.snap(e4, RESPONSE);
        exchangeStore.snap(e5, REQUEST);
        exchangeStore.snap(e5, RESPONSE);
    }

    @Test
    void requestHeaderContainsTest() {
        assertTrue(requestHeaderContains(CONTENT_TYPE,e1));
        assertFalse(requestHeaderContains("Foo",e1));
        assertTrue(requestHeaderContains("Bar",e1));
        assertFalse(requestHeaderContains("Boo",e1));
    }

    @Test
    void responseHeaderContainsTest() {
        assertTrue(responseHeaderContains("html",e1));
        assertTrue(responseHeaderContains("X-Foo",e1));
        assertTrue(responseHeaderContains("ZZZ",e1));
        assertFalse(responseHeaderContains("Boo",e1));
    }

    @Test
    void pathContainsTest() {
        assertTrue(pathContains("foo",e1));
        assertTrue(pathContains("bar",e1));
        assertTrue(pathContains("/foo/bar",e1));
        assertTrue(pathContains("/",e1));
    }

    @Test
    void searchStore() throws Exception {
        ExchangeQueryResult result = exchangeStore.getFilteredSortedPaged(getQueryParameter(""),false);
        assertEquals(5,result.count);
    }

    @Test
    void filterPost() throws Exception {
        ExchangeQueryResult result = exchangeStore.getFilteredSortedPaged(getQueryParameter("?method=POST"),false);
        assertEquals(2,result.count);
    }

    @Test
    void filterPostAndStatusCode() throws Exception {
        ExchangeQueryResult result = exchangeStore.getFilteredSortedPaged(getQueryParameter("?method=POST&statuscode=200"),false);
        assertEquals(1,result.count);
    }

    @Test
    void filterSearch() throws Exception {
        ExchangeQueryResult result = exchangeStore.getFilteredSortedPaged(getQueryParameter("?search=foo"),false);
        assertEquals(2,result.count);
    }

    @NotNull
    private QueryParameter getQueryParameter(String url) throws Exception {
        return new QueryParameter(URLParamUtil.getParams(new URIFactory(), Request.get(url).buildExchange(), ERROR), pattern.matcher(e1.getRequestURI()));
    }
}