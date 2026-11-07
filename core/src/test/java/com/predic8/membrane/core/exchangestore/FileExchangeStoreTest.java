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

import com.predic8.membrane.core.util.ConfigurationException;

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

    @Test
    void idsAreStableAcrossRestarts() throws Exception {
        final var writer = store();
        snap(writer, "/a", "1", 200);
        snap(writer, "/b", "2", 200);
        final var ids = writer.getAllExchangesAsList().stream().map(AbstractExchange::getId).toList();

        final var reader = store();

        assertEquals(ids, reader.getAllExchangesAsList().stream().map(AbstractExchange::getId).toList());
        assertEquals("/b", reader.getExchangeById(ids.getLast()).getRequest().getUri());
        snap(reader, "/c", "3", 200);
        assertTrue(reader.getAllExchangesAsList().getLast().getId() > ids.getLast());
    }

    @Test
    void unreadableExchangeFileDoesNotShrinkTheIndex() throws Exception {
        final var writer = store();
        snap(writer, "/old", "1", 200);
        Thread.sleep(5);
        snap(writer, "/new", "2", 200);
        try (var files = Files.walk(dir)) {
            final var newest = files.filter(f -> f.toString().endsWith("-Exchange.json")).max(FileExchangeStore.NEWEST_FIRST.reversed()).orElseThrow();
            Files.writeString(newest, "{\"trunc");
        }

        final var reader = new FileExchangeStore();
        reader.setDir(dir.toString());
        reader.setMaxIndexed(1);
        reader.init(null);

        assertEquals(List.of("/old"), reader.getAllExchangesAsList().stream().map(e -> e.getRequest().getUri()).toList());
    }

    @Test
    void sortsByNumericCounter() {
        final var names = new ArrayList<>(List.of(Path.of("h10m00s00ms000-9-Exchange.json"), Path.of("h10m00s00ms000-10-Exchange.json"),
                Path.of("h09m00s00ms000-11-Exchange.json")));
        names.sort(FileExchangeStore.NEWEST_FIRST);
        assertEquals(List.of("h10m00s00ms000-10-Exchange.json", "h10m00s00ms000-9-Exchange.json", "h09m00s00ms000-11-Exchange.json"),
                names.stream().map(Path::toString).toList());
    }

    @Test
    void saveBodyOnlyStoresNoHeadersInTheMetadata() throws Exception {
        final var store = store();
        store.setSaveBodyOnly(true);
        final var exc = Request.post("/a").header("Authorization", "Basic c2VjcmV0").body("x").buildExchange();
        exc.setProxy(new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 2000, null), "localhost", 80));
        store.snap(exc, REQUEST);
        exc.setResponse(Response.ok("y").build());
        store.snap(exc, RESPONSE);

        try (var files = Files.walk(dir)) {
            for (final var f : files.filter(Files::isRegularFile).toList())
                assertFalse(Files.readString(f).contains("c2VjcmV0"), f.toString());
        }
    }

    @Test
    void twoStoresWriteIntoTheirOwnDirectories(@TempDir Path other) throws Exception {
        final var first = store();
        final var second = new FileExchangeStore();
        second.setDir(other.toString());
        second.init(null);
        final var exc = Request.post("/a").body("x").buildExchange();
        exc.setProxy(new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 2000, null), "localhost", 80));
        first.snap(exc, REQUEST);
        second.snap(exc, REQUEST);
        exc.setResponse(Response.ok("y").build());
        first.snap(exc, RESPONSE);
        second.snap(exc, RESPONSE);

        try (var a = Files.walk(dir); var b = Files.walk(other)) {
            assertEquals(3, a.filter(Files::isRegularFile).count());
            assertEquals(3, b.filter(Files::isRegularFile).count());
        }
        assertEquals(1, second.getAllExchangesAsList().size());
    }

    @Test
    void repeatedSnapshotUpdatesTheIndexEntry() throws Exception {
        final var store = store();
        final var exc = Request.post("/a").body("x").buildExchange();
        exc.setProxy(new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 2000, null), "localhost", 80));
        store.snap(exc, REQUEST);
        exc.setResponse(Response.ok("y").build());
        store.snap(exc, RESPONSE);
        exc.setStatus(ExchangeState.COMPLETED);
        store.snap(exc, RESPONSE);

        final var all = store.getAllExchangesAsList();
        assertEquals(1, all.size());
        assertEquals(ExchangeState.COMPLETED, all.getFirst().getStatus());
    }

    @Test
    void deletingOldFoldersRemovesTheWholeCutoffDayFromTheIndex() throws Exception {
        final var store = store();
        store.setMaxDays(1);
        final var exc = Request.post("/a").body("x").buildExchange();
        exc.setProxy(new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 2000, null), "localhost", 80));
        final var time = Calendar.getInstance();
        time.clear();
        time.set(2026, Calendar.JANUARY, 10, 10, 0);
        exc.setTime(time);
        store.snap(exc, REQUEST);
        exc.setResponse(Response.ok("y").build());
        store.snap(exc, RESPONSE);

        final var now = Calendar.getInstance();
        now.clear();
        now.set(2026, Calendar.JANUARY, 11, 9, 0); // cutoff 2026-01-10 09:00: the folder of that day is deleted
        store.deleteOldFolders(now);

        assertTrue(store.getAllExchangesAsList().isEmpty());
    }

    @Test
    void rejectsMaxIndexedBelowOne() {
        assertThrows(ConfigurationException.class, () -> new FileExchangeStore().setMaxIndexed(0));
    }

    @Test
    void everyModificationAdvancesTheMarker() throws Exception {
        final var store = store();
        final var before = store.getLastModified();
        snap(store, "/a", "x", 200);
        final var middle = store.getLastModified();
        snap(store, "/b", "x", 200);
        assertTrue(middle > before);
        assertTrue(store.getLastModified() > middle);
    }

    @Test
    void formattedXmlIsRestoredAsUtf8() {
        assertEquals("text/xml; charset=UTF-8", FileExchangeStore.withUtf8Charset("text/xml; charset=ISO-8859-1"));
        assertEquals("application/soap+xml; action=\"x\"; charset=UTF-8", FileExchangeStore.withUtf8Charset("application/soap+xml; action=\"x\""));
    }

    @Test
    void clientStatistics() throws Exception {
        final var store = store();
        snap(store, "/a", "x", 200);
        snap(store, "/b", "x", 200);
        assertEquals(1, store.getClientStatistics().size());
        assertEquals(2, store.getClientStatistics().getFirst().getCount());
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
