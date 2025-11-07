package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.*;

public class SchemaFactory {

    public static final String OBJECT = "object";
    public static final String ARRAY = "array";

    public static SchemaObject object() {
        return object(null);
    }

    public static SchemaObject object(String name) {
        return new SchemaObject(name);
    }

    public static SchemaArray array(String name) {
        return new SchemaArray(name);
    }

    public static SchemaString string(String name) {
        return new SchemaString(name);
    }

    public static SchemaRef ref(String ref) {
        return new SchemaRef(ref);
    }

    public static AnyOf anyOf(List<SchemaObject> anyOfs) {
        var anyOf = new AnyOf(anyOfs);
        anyOf.name = "anyOf";
        return anyOf;
    }

    public static Schema schema(String name) {
        return new Schema(name);
    }

}
