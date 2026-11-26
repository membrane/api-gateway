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
import java.util.Calendar;
import java.util.List;
import java.util.regex.*;

import static com.predic8.membrane.core.exchangestore.AbstractExchangeStore.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;
import static org.junit.jupiter.api.Assertions.*;

public class AbstractExchangeStoreTest {

    private Exchange e1, e2, e3, e4, e5;

    private static final Pattern pattern = Pattern.compile("/.*(/?\\?.*)?");

    private final LimitedMemoryExchangeStore exchangeStore = new LimitedMemoryExchangeStore();

    @BeforeEach
    void setUp() throws URISyntaxException {
        e1 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo","Bar").buildExchange();
        e1.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Foo","XXX ZZZ AAA").build());
        e1.setOriginalRequestUri("/foo/bar");
        e1.setProxy(new APIProxy());

        e2 = Request.post("/products").contentType(APPLICATION_JSON).header("Foo Goo","Bar").buildExchange();
        e2.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Goo","XXX ZZZ AAA").build());
        e2.setOriginalRequestUri("/poo/bar");
        e2.setProxy(new APIProxy());

        e3 = Request.post("/products").contentType(APPLICATION_JSON).header("Zoo Goo","Bar").buildExchange();
        e3.setResponse(Response.badRequest().contentType(TEXT_HTML_UTF8).header("X-OO","XXX ZZZ AAA").build());
        e3.setOriginalRequestUri("/bar");
        e3.setProxy(new APIProxy());

        e4 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo Goo","Bar").buildExchange();
        e4.setResponse(Response.badRequest().contentType(TEXT_HTML_UTF8).header("X-Bar","XXX ZZZ AAA").build());
        e4.setOriginalRequestUri("/foo/bar");
        e4.setProxy(new APIProxy());

        e5 = Request.get("/foo/bar?a=1&b=2&sort=method").contentType(APPLICATION_JSON).header("Goo Goo","Bar").buildExchange();
        e5.setResponse(Response.ok().contentType(TEXT_HTML_UTF8).header("X-Goo","XXX ZZZ AAA").build());
        e5.setOriginalRequestUri("/goo/bar");
        e5.setProxy(new APIProxy());

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

    @Test
    void cursorPageIsStableWhenNewExchangesArrive() throws Exception {
        final var store = storeWithExchangesAt(1000, 2000, 3000, 4000);
        final var firstPage = query(store, "?max=2");
        assertEquals(List.of(4000L, 3000L), ids(firstPage));

        snapAt(store, 5000, 5000);

        assertEquals(List.of(2000L, 1000L), ids(query(store, "?max=2&after=" + ExchangeCursor.of(firstPage.getLast()).encode())));
    }

    @Test
    void beforeReturnsTheExchangesClosestToTheCursor() throws Exception {
        final var store = storeWithExchangesAt(1000, 2000, 3000, 4000, 5000);
        final var cursor = new ExchangeCursor(3000, 3000).encode();

        assertEquals(List.of(4000L), ids(query(store, "?max=1&before=" + cursor)));
        assertEquals(List.of(5000L, 4000L), ids(query(store, "?max=10&before=" + cursor)));
    }

    @Test
    void cursorOrdersExchangesOfTheSameMillisecondById() throws Exception {
        final var store = new LimitedMemoryExchangeStore();
        snapAt(store, 1000, 3);
        snapAt(store, 1000, 7);
        snapAt(store, 1000, 5);

        assertEquals(List.of(7L, 5L, 3L), ids(query(store, "")));
        assertEquals(List.of(3L), ids(query(store, "?after=" + new ExchangeCursor(1000, 5).encode())));
    }

    @Test
    void cursorOfAnEvictedExchangeStillPages() throws Exception {
        final var store = storeWithExchangesAt(1000, 2000, 3000);
        assertEquals(List.of(2000L, 1000L), ids(query(store, "?after=" + new ExchangeCursor(2500, 42).encode())));
    }

    @Test
    void cursorFollowsAscendingOrder() throws Exception {
        final var store = storeWithExchangesAt(1000, 2000, 3000, 4000);
        assertEquals(List.of(3000L, 4000L), ids(query(store, "?order=asc&after=" + new ExchangeCursor(2000, 2000).encode())));
    }

    @Test
    void invalidCursorQueriesAreRejected() {
        final var store = storeWithExchangesAt(1000);
        final var cursor = new ExchangeCursor(1000, 1000).encode();
        for (final var query : List.of(
                "?after=garbage",
                "?after=1000_x",
                "?after=" + cursor + "&before=" + cursor,
                "?offset=1&after=" + cursor,
                "?after=1000_1000_GET",
                "?sort=statusCode&after=1000_1000_ok",
                "?sort=reqContentType&after=" + cursor)) {
            assertThrows(InvalidExchangeQueryException.class, () -> query(store, query), query);
        }
    }

    @Test
    void cursorPagesBySortValueThenTime() throws Exception {
        final var store = new LimitedMemoryExchangeStore();
        snapAt(store, 1000, 1, 404);
        snapAt(store, 2000, 2, 200);
        snapAt(store, 3000, 3, 500);
        snapAt(store, 4000, 4, 200);
        snapAt(store, 5000, 5, 404);

        final var firstPage = query(store, "?sort=statusCode&max=2");
        assertEquals(List.of(3L, 5L), ids(firstPage));

        snapAt(store, 6000, 6, 500);

        final var cursor = ExchangeCursor.of(firstPage.getLast(), ExchangeSortKey.STATUS_CODE).encode();
        assertEquals("5000_5_404", cursor);
        assertEquals(List.of(1L, 4L, 2L), ids(query(store, "?sort=statusCode&after=" + cursor)));
        assertEquals(List.of(6L, 3L), ids(query(store, "?sort=statusCode&before=" + cursor)));
        assertEquals(List.of(1L, 5L, 3L, 6L), ids(query(store, "?sort=statusCode&order=asc&after=4000_4_200")));
    }

    @Test
    void cursorOfAnEvictedExchangePagesBySortValue() throws Exception {
        final var store = new LimitedMemoryExchangeStore();
        snapAt(store, 1000, 1, 200);
        snapAt(store, 2000, 2, 404);
        snapAt(store, 3000, 3, 500);

        assertEquals(List.of(2L, 1L), ids(query(store, "?sort=statusCode&after=9000_9_404")));
    }

    @Test
    void exchangesWithoutResponseSortFirstAscending() throws Exception {
        final var store = new LimitedMemoryExchangeStore();
        snapAt(store, 1000, 1, 200);
        final var inFlight = exchangeAt(2000, 2);
        store.snap(inFlight, REQUEST);

        assertEquals(List.of(2L, 1L), ids(query(store, "?sort=duration&order=asc")));
        final var cursor = ExchangeCursor.of(inFlight, ExchangeSortKey.DURATION).encode();
        assertEquals("2000_2", cursor);
        assertEquals(List.of(1L), ids(query(store, "?sort=duration&order=asc&after=" + cursor)));
    }

    private static LimitedMemoryExchangeStore storeWithExchangesAt(long... times) {
        final var store = new LimitedMemoryExchangeStore();
        for (final var time : times) {
            snapAt(store, time, time);
        }
        return store;
    }

    private static void snapAt(LimitedMemoryExchangeStore store, long time, long id) {
        snapAt(store, time, id, 200);
    }

    private static void snapAt(LimitedMemoryExchangeStore store, long time, long id, int statusCode) {
        final var exc = exchangeAt(time, id);
        exc.setResponse(Response.statusCode(statusCode).build());
        store.snap(exc, REQUEST);
        store.snap(exc, RESPONSE);
    }

    private static Exchange exchangeAt(long time, long id) {
        try {
            final var exc = Request.get("/calls/" + id).buildExchange();
            exc.setProxy(new APIProxy());
            exc.setId(id);
            final var calendar = Calendar.getInstance();
            calendar.setTimeInMillis(time);
            exc.setTime(calendar);
            return exc;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private List<AbstractExchange> query(LimitedMemoryExchangeStore store, String query) throws Exception {
        return store.getFilteredSortedPaged(getQueryParameter(query), false).getExchanges();
    }

    private static List<Long> ids(List<AbstractExchange> exchanges) {
        return exchanges.stream().map(AbstractExchange::getId).toList();
    }

    @NotNull
    private QueryParameter getQueryParameter(String url) throws Exception {
        return new QueryParameter(URLParamUtil.getParams(new URIFactory(), Request.get(url).buildExchange(), ERROR), pattern.matcher(e1.getRequest().getUri()));
    }
}