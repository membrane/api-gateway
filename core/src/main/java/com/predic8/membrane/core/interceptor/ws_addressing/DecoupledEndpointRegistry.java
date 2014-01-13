package com.predic8.membrane.core.interceptor.ws_addressing;

import java.util.HashMap;
import java.util.Map;

public class DecoupledEndpointRegistry {
    private final Map<String, String> registry = new HashMap<String, String>();

    public void register(String id, String url) {
        registry.put(id, url);
    }

    public String lookup(String id) {
        return registry.get(id);
    }

    @Override
    public String toString() {
        return "DecoupledEndpointRegistry: " + registry.toString();
    }
}