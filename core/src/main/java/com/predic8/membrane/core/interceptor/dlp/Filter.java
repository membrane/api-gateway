package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@MCElement(name = "filter")
public class Filter extends Action {

    private static final Logger log = LoggerFactory.getLogger(Filter.class);

    private static final Configuration SAFE_CONFIG = Configuration.builder()
            .options(Set.of(Option.DEFAULT_PATH_LEAF_TO_NULL))
            .build();

    @Override
    public String apply(DLPContext context) {
        try {
            DocumentContext doc = JsonPath.using(SAFE_CONFIG).parse(context.getBody());
            Object value = doc.read(getField());

            if (value == null) {
                return doc.jsonString();
            }

            doc.delete(getField());

            log.info("[Filter] Removed field {} with value: {}", getField(), value);
            return doc.jsonString();

        } catch (Exception e) {
            log.error("[Filter] Failed to apply filter on field: {}", getField(), e);
            throw new RuntimeException("Failed to apply filter on field: " + getField(), e);
        }
    }
}
