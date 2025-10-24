package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.*;

public class SchemaArray extends AbstractSchema<SchemaArray> {

    public SchemaArray() {
        type("array");
    }

    public SchemaArray(String name) {
        super(name);
        type("array");
    }

    public SchemaArray items(SchemaObject items) {
        addAttribute("items", items);
        return this;
    }
}
