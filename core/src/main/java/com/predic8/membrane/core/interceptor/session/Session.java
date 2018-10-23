package com.predic8.membrane.core.interceptor.session;

import java.util.*;
import java.util.stream.Collectors;

public class Session {

    Map<String, Object> content;

    public Session(Map<String, Object> content) {
        this.content = content;
    }

    public <T> T get(String key) {
        return (T) getValues(key).get(key);
    }

    public void remove(String key) {
        removeValues(key);
    }

    public <T> void put(String key, T value) {
        putValues(Collections.singletonMap(key, value));
    }

    public Map<String, Object> getValues(String... keys) {
        Set<String> keysUnique = new HashSet<>(Arrays.asList(keys));
        return content
                .entrySet()
                .stream()
                .filter(entry -> keysUnique.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
    }

    public void removeValues(String... keys) {
        Set<String> keysUnique = new HashSet<>(Arrays.asList(keys));
        content
                .entrySet()
                .stream()
                .filter(entry -> !keysUnique.contains(entry.getKey()))
                .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
    }

    public void putValues(Map<String, Object> map) {
        content.putAll(map);
    }

    public Map<String, Object> get() {
        return content;
    }

    public void clear() {
        content.clear();
    }

}
