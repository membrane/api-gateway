package com.predic8.membrane.core.lang.spel;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

class PropertiesMap implements Map<String, String> {

    private final Map<String, Object> properties;

    PropertiesMap(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public int size() {
        return properties.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public String get(Object key) {
        return null;
    }

    @Override
    public String put(String key, String value) {
        return null;
    }

    @Override
    public String remove(Object key) {
        return null;
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> m) {

    }

    @Override
    public void clear() {

    }

    @Override
    public Set<String> keySet() {
        return null;
    }

    @Override
    public Collection<String> values() {
        return null;
    }

    @Override
    public Set<Entry<String, String>> entrySet() {
        return null;
    }
}
