package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @description <p>Removes a field from the JSON payload entirely.</p>
 *
 * <p>This is typically used to eliminate sensitive or irrelevant data before logging or forwarding.</p>
 *
 * <h2>Attributes</h2>
 * <ul>
 *     <li>{@code field} ? The JSONPath to the field to remove.</li>
 * </ul>
 * @example <filter field="$.password" />
 */
@MCElement(name = "filter")
public class Filter extends Action {

    private static final Logger log = LoggerFactory.getLogger(Filter.class);

    private static final Configuration SAFE_CONFIG = Configuration.builder()
            .options(Set.of(Option.DEFAULT_PATH_LEAF_TO_NULL))
            .build();

    @Override
    public String apply(DLPContext context) {
        DocumentContext doc = JsonPath.using(SAFE_CONFIG).parse(context.body());
        String original = doc.read(getField(), String.class);

        if (original == null) {
            log.info("[Filter]: Field '{}' not found!", getField());
            return doc.jsonString();
        }

        doc.delete(getField());
        log.info("[Filter]: Field='{}' | Value='{}'", getField(), original);
        return doc.jsonString();
    }
}
