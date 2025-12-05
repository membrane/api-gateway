package com.predic8.membrane.annot.util;

import com.predic8.membrane.annot.Grammar;

import java.util.HashMap;
import java.util.List;

import static java.util.List.of;

public class GrammarMock implements Grammar {

    private HashMap<String, Class<?>> elements = new HashMap<>();

    @Override
    public Class<?> getElement(String key) {
        return elements.get(key);
    }

    @Override
    public Class<?> getLocal(String context, String key) {
        return null;
    }

    @Override
    public List<String> getCrdSingularNames() {
        return of();
    }

    @Override
    public String getSchemaLocation() {
        return "";
    }

    public GrammarMock withGlobalElement(String key, Class<?> clazz) {
        elements.put(key, clazz);
        return this;
    }
}
