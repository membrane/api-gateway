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

import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * A custom implementation of a {@link Map} that wraps a {@link Header} object. This class provides
 * a mapping of header names to their corresponding values as strings while adhering to the Map interface.
 *
 * The {@code HeaderMap} behaves like a standard {@code Map<String, String>}, where:
 * - Keys represent unique header names from the {@code Header} object.
 * - Values are the concatenated string representations of the corresponding header values.
 *
 * Modifications to the map (e.g., adding, removing, or updating entries) directly affect the underlying {@code Header}.
 * Similarly, retrieval operations reflect the current state of the {@code Header}.
 *
 * This class is primarily intended for working with headers in a map-like structure, making it easier
 * to access, modify, or iterate over header information.
 *
 * Thread-Safety:
 * This class does not ensure thread-safety. Access and modifications to the underlying {@code Header}
 * object should be externally synchronized if used in concurrent environments.
 */
public class HeaderMap implements Map<String, String> {

    private final Header header;

    public HeaderMap(Header header) {
        this.header = Objects.requireNonNull(header);
    }

    @Override
    public int size() {
        return header.getUniqueHeaderNames().size();
    }

    @Override
    public boolean isEmpty() {
        return header.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof String && get(key) != null;
    }

    @Override
    public boolean containsValue(Object value) {
        if (!(value instanceof String s))
            return false;

        for (String k : header.getUniqueHeaderNames()) {
            if (Objects.equals(s, header.getValuesAsString(k)))
                return true;
        }
        return false;
    }

    @Override
    public String get(Object key) {
        if (!(key instanceof String k))
            return null;
        var value = header.getValuesAsString(k);
        if (value != null)
            return value;
        return header.getValuesAsString(TextUtil.camelToKebab(k));
    }

    /**
     * Alias to keep compatibility with groovy scripting variables before version 7
     * @param key
     * @param value
     * @return
     */
    public @Nullable String add(String key, String value) {
        return put(key, value);
    }

    @Override
    public @Nullable String put(String key, String value) {
        String old = header.getValuesAsString(key);
        remove(key);
        header.add(key, value);
        return old;
    }

    @Override
    public String remove(Object key) {
        if (!(key instanceof String k))
            return null;

        String old = null;
        List<HeaderField> toRemove = new ArrayList<>();
        for(HeaderField hf : header.getFields()) {
            if(hf.getHeaderName().hasName(k)) {
                old = hf.getValue();
                toRemove.add(hf);
            }
        }
        toRemove.forEach(header::remove); // Avoid ConcurrentModificationException
        return old;
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends String> m) {
        for (Entry<? extends String, ? extends String> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        header.clear();
    }

    @Override
    public @NotNull Set<String> keySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<String> iterator() {
                return header.getUniqueHeaderNames().iterator();
            }

            @Override
            public int size() {
                return header.getUniqueHeaderNames().size();
            }
        };
    }

    @Override
    public @NotNull Collection<String> values() {
        return new AbstractCollection<>() {
            @Override
            public Iterator<String> iterator() {
                Iterator<String> keys = header.getUniqueHeaderNames().iterator();
                return new Iterator<>() {

                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public String next() {
                        return header.getValuesAsString(keys.next());
                    }
                };
            }

            @Override
            public int size() {
                return HeaderMap.this.size();
            }
        };
    }

    @Override
    public @NotNull Set<Entry<String, String>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<String, String>> iterator() {
                Iterator<String> keys = header.getUniqueHeaderNames().iterator();

                return new Iterator<>() {
                    @Override public boolean hasNext() { return keys.hasNext(); }

                    @Override
                    public Entry<String, String> next() {
                        String k = keys.next();
                        String v = header.getValuesAsString(k);

                        return new AbstractMap.SimpleEntry<>(k, v) {
                            @Override
                            public String setValue(String newValue) {
                                return HeaderMap.this.put(k, newValue);
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return HeaderMap.this.size();
            }
        };
    }
}
