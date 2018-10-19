package com.predic8.membrane.core.interceptor.session;

import java.util.*;
import java.util.stream.Collectors;

public class Session {

    Map<String, String> content;

    public Session(Map<String, String> content) {
        this.content = content;
    }

    public String get(String key) {
        return getValues(key).get(key);
    }

    public void remove(String key) {
        removeValues(key);
    }

    public void put(String value, String s) {
        putValues(Collections.singletonMap(value, s));
    }

    public Map<String, String> getValues(String... keys) {
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

    public void putValues(Map<String, String> map) {
        content.putAll(map);
    }

    public Map<String, String> get() {
        return content;
    }

    public void clear() {
        content.clear();
    }

}
