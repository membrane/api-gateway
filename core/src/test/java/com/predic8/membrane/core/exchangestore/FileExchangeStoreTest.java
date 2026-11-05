/* Copyright 2026 predic8 GmbH, www.predic8.com

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
import com.predic8.membrane.core.proxies.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

import java.nio.file.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static org.junit.jupiter.api.Assertions.*;

class FileExchangeStoreTest {

    @TempDir
    Path dir;

    @Test
    void requestAndResponseShareTheirNumber() throws Exception {
        final var store = store();
        snap(store, "/a", "{\"a\":1}", 201);

        final var names = Files.walk(dir).filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();

        assertEquals(3, names.size(), names.toString());
        final var base = names.getFirst().substring(0, names.getFirst().lastIndexOf('-'));
        assertEquals(List.of(base + "-Exchange.json", base + "-Request.msg", base + "-Response.msg"), names);
    }

    @Test
    void listsExchangesWithoutBodies() throws Exception {
        final var store = store();
        snap(store, "/a", "{\"a\":1}", 201);

        final var all = store.getAllExchangesAsList();

        assertEquals(1, all.size());
        final var exc = all.getFirst();
        assertEquals("POST", exc.getRequest().getMethod());
        assertEquals("/a", exc.getRequest().getUri());
        assertEquals("application/json", exc.getRequestContentType());
        assertEquals(201, exc.getResponse().getStatusCode());
        assertEquals("shop", exc.getProxy().getName());
        assertEquals(2000, exc.getProxy().getKey().getPort());
        assertEquals("localhost", exc.getServer());
        assertInstanceOf(EmptyBody.class, exc.getRequest().getBody());
        assertEquals(7, exc.getRequestContentLength()); // recorded header, although the body is not loaded
    }

    @Test
    void getExchangeByIdReadsBodies() throws Exception {
        final var store = store();
        snap(store, "/a", "{\"a\":1}", 201);
        final var id = store.getAllExchangesAsList().getFirst().getId();

        final var exc = store.getExchangeById(id);

        assertEquals("{\"a\":1}", exc.getRequest().getBodyAsStringDecoded());
        assertEquals("response-of-/a", exc.getResponse().getBodyAsStringDecoded());
        assertNull(store.getExchangeById(id + 1000));
    }

    @Test
    void readsBodyOnlyFiles() throws Exception {
        final var store = store();
        store.setSaveBodyOnly(true);
        snap(store, "/a", "plain", 200);

        final var exc = store.getExchangeById(store.getAllExchangesAsList().getFirst().getId());

        assertEquals("plain", exc.getRequest().getBodyAsStringDecoded());
        assertEquals("/a", exc.getRequest().getUri());
    }

    @Test
    void newStoreIndexesExistingFilesNewestFirst() throws Exception {
        final var writer = store();
        snap(writer, "/old", "1", 200);
        Thread.sleep(5);
        snap(writer, "/new", "2", 200);

        final var reader = new FileExchangeStore();
        reader.setDir(dir.toString());
        reader.setMaxIndexed(1);
        reader.init(null);

        final var all = reader.getAllExchangesAsList();
        assertEquals(1, all.size());
        assertEquals("/new", all.getFirst().getRequest().getUri());
    }

    @Test
    void indexIsLimited() throws Exception {
        final var store = store();
        store.setMaxIndexed(2);
        for (int i = 0; i < 4; i++)
            snap(store, "/" + i, "x", 200);

        assertEquals(List.of("/2", "/3"), store.getAllExchangesAsList().stream().map(e -> e.getRequest().getUri()).toList());
    }

    @Test
    void waitForModificationReturnsAfterSnap() throws Exception {
        final var store = store();
        final var known = store.getLastModified();
        Thread.sleep(2);
        snap(store, "/a", "x", 200);
        assertTrue(store.waitForModification(known, 5_000));
        assertFalse(store.waitForModification(store.getLastModified(), 50));
    }

    @Test
    void deletingOldFoldersRemovesThemFromTheIndex() throws Exception {
        final var store = store();
        store.setMaxDays(1);
        snap(store, "/a", "x", 200);

        final var future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_MONTH, 3);
        store.deleteOldFolders(future);

        assertTrue(store.getAllExchangesAsList().isEmpty());
    }

    /**
     * maxDays is set from the configuration after the constructor ran, so the cleanup must be scheduled in init().
     */
    @Test
    void configuredMaxDaysSchedulesCleanup() {
        final var store = new FileExchangeStore();
        store.setDir(dir.toString());
        store.setMaxDays(7);
        store.init(null);
        assertTrue(store.isCleanupScheduled());
    }

    @Test
    void noCleanupWithoutMaxDays() {
        assertFalse(store().isCleanupScheduled());
    }

    private FileExchangeStore store() {
        final var store = new FileExchangeStore();
        store.setDir(dir.toString());
        store.init(null);
        return store;
    }

    private static void snap(FileExchangeStore store, String path, String body, int status) throws Exception {
        final var exc = Request.post(path).contentType(path.equals("/a") && body.startsWith("{") ? "application/json" : "text/plain")
                .body(body).buildExchange();
        final var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 2000, null), "localhost", 80);
        proxy.setName("shop");
        exc.setProxy(proxy);
        store.snap(exc, REQUEST);
        exc.setResponse(Response.statusCode(status).contentType("text/plain").body("response-of-" + path).build());
        exc.setStatus(ExchangeState.COMPLETED);
        store.snap(exc, RESPONSE);
    }
}
