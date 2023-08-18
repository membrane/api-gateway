package com.predic8.membrane.core.lang.spel;

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.HeaderField;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class HeaderMap implements Map<String, String> {

    private Header headers;

    public HeaderMap(Header headers) {
        this.headers = headers;
    }

    @Override
    public int size() {
        return headers.getAllHeaderFields().length;
    }

    @Override
    public boolean isEmpty() {
        return headers.getAllHeaderFields().length == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return headers.contains(key.toString());
    }

    @Override
    public boolean containsValue(Object value) {
        return Arrays.stream(headers.getAllHeaderFields())
                .anyMatch(headerField -> headerField.getValue().equals(value.toString()));
    }

    @Override
    public String get(Object key) {
        return Arrays.stream(headers.getAllHeaderFields())
                .filter(headerField -> headerField.getHeaderName().equals(key.toString()))
                .findFirst()
                .map(HeaderField::getValue)
                .orElse(null);
    }

    @Nullable
    @Override
    public String put(String key, String value) {
        var oldValue = get(key);

        headers.add(key, value);

        return oldValue;
    }

    @Override
    public String remove(Object key) {
        var oldValue = get(key);

        headers.removeFields(key.toString());

        return oldValue;
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends String> m) {
        m.forEach((key, value) -> headers.add(key, value));
    }

    @Override
    public void clear() {
        headers.clear();
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return Arrays.stream(headers.getAllHeaderFields())
                .map(headerField -> headerField.getHeaderName().toString())
                .collect(Collectors.toSet());
    }

    @NotNull
    @Override
    public Collection<String> values() {
        return Arrays.stream(headers.getAllHeaderFields())
                .map(HeaderField::getValue)
                .toList();
    }

    @NotNull
    @Override
    public Set<Entry<String, String>> entrySet() {
        return Arrays.stream(headers.getAllHeaderFields())
                .map(headerField -> Map.entry(headerField.getHeaderName().toString(), headerField.getValue()))
                .collect(Collectors.toSet());
    }
}
