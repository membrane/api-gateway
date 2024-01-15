package com.predic8.membrane.core.interceptor.apikey.stores;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyFileStore.parseLine;
import static java.util.List.of;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ApiKeyFileStoreTest {

    private static final HashMap<String, Optional<List<String>>> EXPECTED_API_KEYS = new HashMap<>() {{
        put("5XF27", Optional.of(List.of("finance", "internal")));
        put("73D29", Optional.of(List.of("accounting", "management")));
        put("89D5C", Optional.of(List.of("internal")));
        put("NMB3B", Optional.of(List.of("demo", "test")));
        put("L62NA", Optional.empty());
        put("G62NB", Optional.empty());
    }};
    private static final Stream<String> LINES = Stream.of(
            "# These are demo-keys.",
            "5XF27:finance,internal",
            "73D29: accounting, management",
            "89D5C: internal,",
            "NMB3B: demo, test # This is an inline comment.",
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
        assertEquals(Optional.of(of("finance", "internal")), store.getScopes("5XF27"));
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

    @Test
    void parseLineTest() {
        assertEquals(new SimpleEntry<>("5XF27", Optional.of(of("finance", "internal"))), parseLine("5XF27: finance , internal "));
        assertEquals(new SimpleEntry<>("89D5C", Optional.of(of("internal"))), parseLine("89D5C: internal,"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), parseLine("L62NA"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), parseLine("L62NA: "));
    }

    @Test
    void parseValues() {
        assertEquals(of(""), ApiKeyFileStore.parseValues(""));
        assertEquals(of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar "));
        assertEquals(of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar ,"));
    }

    private static void loadFromFile(ApiKeyFileStore store, String path) {
        store.setLocation(requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource(path)).getPath());
        //noinspection DataFlowIssue
        store.onApplicationEvent(null);
    }
}