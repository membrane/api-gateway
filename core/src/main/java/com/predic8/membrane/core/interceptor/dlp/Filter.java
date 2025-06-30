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
            for (Field p : fields) {
                try {
                    context.delete(p.getJsonpath());
                } catch (Exception e) {
                }
            }
            System.out.println(context.jsonString());
            return context.jsonString();
        } catch (Exception e) {
            throw new RuntimeException("DLP Filter failed to apply JSONPath deletions", e);
        }
    }

    @MCChildElement
    public Filter setPaths(List<Field> fields) {
        this.fields = fields;
        return this;
    }

    public List<Field> getPaths() {
        return fields;
    }
}
