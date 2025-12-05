/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.http.Header;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;

class HeaderMapTest {

    private Header newHeader() {
        return new Header();
    }

    @Test
    void empty() {
        HeaderMap map = new HeaderMap(newHeader());

        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.containsKey("X"));
        assertFalse(map.containsValue("foo"));
        assertNull(map.get("X"));
    }

    @Test
    void read() {
        Header h = newHeader();
        h.add(CONTENT_TYPE, APPLICATION_JSON);
        HeaderMap map = new HeaderMap(h);
        assertEquals(APPLICATION_JSON, map.get(CONTENT_TYPE));
        assertEquals(APPLICATION_JSON, map.get("content-type"));
        assertEquals(APPLICATION_JSON, map.get("cOnteNt-TyPe"));
        assertEquals(APPLICATION_JSON, map.get("ContentType"));
        assertEquals(APPLICATION_JSON, map.get("contentType"));
    }

    @Test
    void testPutAndGetSingleValue() {
        Header h = newHeader();
        HeaderMap map = new HeaderMap(h);

        assertNull(map.put("X", "1"));
        assertEquals("1", map.get("X"));
        assertEquals(1, map.size());
        assertTrue(map.containsKey("X"));
        assertTrue(map.containsValue("1"));
    }

    @Test
    void putReplacesValue() {
        Header h = newHeader();
        HeaderMap map = new HeaderMap(h);

        map.put("X", "1");
        assertEquals("1", map.put("X", "2"));
        assertEquals("2", map.get("X"));
        assertEquals(1, map.size());
    }

    @Test
    void remove() {
        Header h = newHeader();
        HeaderMap map = new HeaderMap(h);

        map.put("X", "1");

        assertEquals("1", map.remove("X"));
        assertNull(map.get("X"));
        assertFalse(map.containsKey("X"));
        assertEquals(0, map.size());
    }

    @Test
    void putAll() {
        HeaderMap map = new HeaderMap(newHeader());

        Map<String, String> input = Map.of(
                "A", "a",
                "B", "b"
        );

        map.putAll(input);

        assertEquals(2, map.size());
        assertEquals("a", map.get("A"));
        assertEquals("b", map.get("B"));
    }

    @Test
    void clear() {
        HeaderMap map = new HeaderMap(newHeader());
        map.put("X", "1");
        map.put("Y", "2");

        map.clear();

        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    void keySet() {
        HeaderMap map = new HeaderMap(newHeader());
        map.put("X", "1");
        map.put("Y", "2");

        Set<String> keys = map.keySet();

        assertEquals(Set.of("X", "Y"), keys);
        assertEquals(2, keys.size());
        assertTrue(keys.contains("X"));
        assertTrue(keys.contains("Y"));
    }

    @Test
    void values() {
        Header h = new Header();
        h.add("X-Foo", "1");
        HeaderMap map = new HeaderMap(h);

        map.put("A", "1");
        map.put("B", "2");

        Collection<String> values = map.values();
        assertEquals(3, values.size());
        assertTrue(values.contains("1"));
        assertTrue(values.contains("2"));
    }

    @Test
    void entrySet() {
        HeaderMap map = new HeaderMap(newHeader());

        map.put("A", "1");
        map.put("B", "2");

        Set<Map.Entry<String, String>> es = map.entrySet();

        assertEquals(2, es.size());
        assertTrue(es.contains(Map.entry("A", "1")));
        assertTrue(es.contains(Map.entry("B", "2")));
    }

    @Test
    void multiValueHeaderIsJoinedCorrectly() {
        Header h = newHeader();

        h.add("X", "1");
        h.add("X", "2");

        HeaderMap map = new HeaderMap(h);

        // HeaderMap sollte Komma-separiert zurückgeben (abhängig von Header)
        String result = map.get("X");

        assertTrue(result.equals("1,2") || result.equals("1, 2"));
        assertEquals(1, map.size());  // unique header names
    }
}
