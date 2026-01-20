package com.predic8.membrane.annot.yaml.spel;

public class SpELContext {

    @SuppressWarnings("unused")
    public String env(String key) {
        return System.getenv(key);
    }

    @SuppressWarnings("unused")
    public String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return v != null ? v : defaultValue;
    }

}
