package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "filter")
public class Filter extends Action {
    @Override
    public String apply(String json) {
        try {
            DocumentContext context = JsonPath.parse(json);
            context.delete(getField());
            return context.jsonString();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
}
