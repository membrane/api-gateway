package com.predic8.membrane.annot.generator.kubernetes.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.*;

public class RefObjSerializer extends JsonSerializer<RefObj> {
    @Override
    public void serialize(RefObj value, JsonGenerator gen,
                          SerializerProvider serializers) throws IOException {
        gen.writeString(value.getPath());
    }
}
