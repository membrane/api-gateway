package com.predic8.membrane.annot.generator.kubernetes.model;

public class RefObj {

    private final String path;

    public RefObj(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "{\"$ref\":\"" + path + "\"}";
    }
}
