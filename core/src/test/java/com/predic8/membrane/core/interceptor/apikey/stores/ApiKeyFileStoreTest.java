/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.apikey.stores;

import com.predic8.membrane.core.*;
import org.junit.jupiter.api.*;

import java.util.AbstractMap.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

public class ApiKeyFileStoreTest {

    private static final HashMap<String, Optional<Set<String>>> EXPECTED_API_KEYS = new HashMap<>() {{
        put("5XF27", Optional.of(Set.of("finance", "internal")));
        put("73D29", Optional.of(Set.of("accounting", "management")));
        put("89D5C", Optional.of(Set.of("internal")));
        put("NMB3B", Optional.of(Set.of("demo", "test")));
        put("L62NA", Optional.empty());
        put("G62NB", Optional.empty());
    }};
    private static final Stream<String> LINES = Stream.of(
            "# These are demo-keys.",
            "",
            "5XF27:finance,internal",
            "73D29: accounting, management",
            "89D5C: internal,",
            "NMB3B: demo, test # This is an inline comment.",
            "",
            "L62NA",
            "G62NB:"
    );

    static ApiKeyFileStore store;

    @BeforeEach
    public void setUp() {
        store = new ApiKeyFileStore();
        loadFromFile(store,"apikeys/keys.txt");
    }

    @Test
    public void readKeyData() throws Exception {
        assertEquals(EXPECTED_API_KEYS, ApiKeyFileStore.readKeyData(LINES));
    }

    @Test
    void getScope() throws UnauthorizedApiKeyException {
        assertEquals(Optional.of(Set.of("finance", "internal")), store.getScopes("5XF27"));
    }

    @Test
    void keyWithoutScopes() throws UnauthorizedApiKeyException {
        assertEquals(Optional.empty(), store.getScopes("L63NC"));
    }

    @Test
    void duplicateKey() {
        assertThrows(Exception.class, () -> loadFromFile(store,"apikeys/duplicate-api-keys.txt"));
    }

    @Test
    void keyNotFound() {
        assertThrows(UnauthorizedApiKeyException.class, () -> store.getScopes("5AF27"));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void inlineComment() throws UnauthorizedApiKeyException {assertFalse(store.getScopes("NMB3B").get().contains("This is an inline comment."));}

    @Test
    void parseLineTest() {
        assertEquals(new SimpleEntry<>("5XF27", Optional.of(Set.of("finance", "internal"))), parseLine("5XF27: finance , internal "));
        assertEquals(new SimpleEntry<>("89D5C", Optional.of(Set.of("internal"))), parseLine("89D5C: internal,"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), parseLine("L62NA"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), parseLine("L62NA: "));
    }

    @Test
    void parseValues() {
        assertEquals(Set.of(""), ApiKeyFileStore.parseValues(""));
        assertEquals(Set.of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar "));
        assertEquals(Set.of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar ,"));
    }

    private static void loadFromFile(ApiKeyFileStore store, String path) {
        store.setLocation(requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource(path)).getPath());
        store.init(new Router());
    }

    @Test
    void extractKeyBeforeHash() {
        assertEquals("Test", ApiKeyFileStore.extractKeyBeforeHash("Test# Demo # Test2"));
        assertEquals("", ApiKeyFileStore.extractKeyBeforeHash("# Full Line Comment"));
    }
}