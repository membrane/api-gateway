package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * @description <p>Logs the risk level and category of a specified field, based on the configured CSV mapping.</p>
 *
 * <p>This action does not modify the message payload. It only writes risk metadata to the application logs.</p>
 *
 * <h2>Attributes</h2>
 * <ul>
 *     <li>{@code field} ? The JSONPath to the field to report.</li>
 * </ul>
 * @example <report field="$.email" />
 */
@MCElement(name = "report")
public class Report extends Action {

    private static final Logger log = LoggerFactory.getLogger(Report.class);

    private static final Configuration SAFE_CONFIG = Configuration.builder()
            .options(Set.of(Option.DEFAULT_PATH_LEAF_TO_NULL))
            .build();

    @Override
    public String apply(DLPContext context) {
        DocumentContext doc = JsonPath.using(SAFE_CONFIG).parse(context.body());
        String original = doc.read(getField(), String.class);

        if (original == null) {
            log.info("[Report]: Field '{}' not found!", getField());
            return doc.jsonString();
        }

        String category = context.riskReport().getCategoryOf(getField());
        String riskLevel = context.riskReport().getMatchedFields().getOrDefault(getField(), "Unknown");

        log.info("[Report]: Field='{}' | Value='{}' | Category='{}' | Risk Level='{}'",
                getField(), original, category, riskLevel);

        return doc.jsonString();
    }
}