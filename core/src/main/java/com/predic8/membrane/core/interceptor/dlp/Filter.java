package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

@MCElement(name = "filter")
public class Filter {

    private List<Field> fields = new ArrayList<>();

    public String apply(String json) {
        try {
            DocumentContext context = JsonPath.parse(json);
            for (Field f : fields) {
                try {
                    context.delete(f.getJsonpath());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return context.jsonString();
        } catch (Exception e) {
            throw new RuntimeException("DLP Filter failed to apply JSONPath deletions", e);
        }
    }

    @MCChildElement
    public Filter setFields(List<Field> fields) {
        this.fields = fields;
        return this;
    }

    public List<Field> getFields() {
        return fields;
    }
}
