package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.core.interceptor.apikey.stores.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.apikey.ApiKeyUtils.*;
import static java.util.Objects.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyUtilsTest {

    @Test
    void readFileTest() throws IOException {
        List<String> lines = readFile(getLocationPath()).toList();
        assertEquals(5, lines.size());
        assertEquals("5XF27: finance,internal", lines.get(0));
    }

    private static String getLocationPath() {
        return requireNonNull(ApiKeyFileStoreTest.class.getClassLoader().getResource("apikeys/keys.txt")).getPath();
    }
}