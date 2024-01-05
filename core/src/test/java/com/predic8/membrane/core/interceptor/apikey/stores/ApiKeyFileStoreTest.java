package com.predic8.membrane.core.interceptor.apikey.stores;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.List.of;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyFileStoreTest {

    static ApiKeyFileStore f;

    @BeforeAll
    public static void setUp() {
        f = new ApiKeyFileStore();
        loadFile("apikeys/keys.txt");
    }

    @Test
    void readFile() throws IOException {
        //noinspection OptionalGetWithoutIsPresent
        var firstLine = f.readFile().findFirst().get();

        assertEquals("5XF27:finance,internal", firstLine);
    }

    private static final HashMap<String, Optional<List<String>>> API_KEYS = new HashMap<>() {{
            put("5XF27", Optional.of(List.of("finance", "internal")));
            put("73D29", Optional.of(List.of("accounting", "management")));
            put("89D5C", Optional.of(List.of("internal")));
            put("L62NA", Optional.empty());
            put("G62NB", Optional.empty());
    }};
    private static final Stream<String> LINES = Stream.of(
     "5XF27:finance,internal",
            "73D29: accounting, management",
            "89D5C: internal,",
            "L62NA",
            "L62NA",
            "G62NB:"
    );

    @Test
    public void readKeyData() throws Exception {
        assertEquals(API_KEYS, ApiKeyFileStore.readKeyData(LINES));
    }

    @Test
    void getScopes() throws UnauthorizedKeyException {
        assertEquals(Optional.of(of("finance", "internal")), f.getScopes("5XF27"));
        loadFile("apikeys/keys2.txt");
        assertEquals(Optional.empty(), f.getScopes("L62NA"));
        assertThrows(UnauthorizedKeyException.class, () -> f.getScopes("5AF27"));
    }

    @Test
    void parseLine() {
        assertEquals(new SimpleEntry<>("5XF27", Optional.of(of("finance", "internal"))), ApiKeyFileStore.parseLine("5XF27: finance , internal "));
        assertEquals(new SimpleEntry<>("89D5C", Optional.of(of("internal"))), ApiKeyFileStore.parseLine("89D5C: internal,"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), ApiKeyFileStore.parseLine("L62NA"));
        assertEquals(new SimpleEntry<>("L62NA", Optional.empty()), ApiKeyFileStore.parseLine("L62NA: "));
    }

    @Test
    void parseValues() {
        assertEquals(of(""), ApiKeyFileStore.parseValues(""));
        assertEquals(of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar "));
        assertEquals(of("foo", "bar"), ApiKeyFileStore.parseValues(" foo, bar ,"));
    }

    private static void loadFile(String path) {
        f.setLocation(requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource(path)).getPath());
        //noinspection DataFlowIssue
        f.onApplicationEvent(null);
    }
}