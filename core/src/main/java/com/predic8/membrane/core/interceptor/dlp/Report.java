package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@MCElement(name = "report")
public class Report extends Action {

    private static final Logger log = LoggerFactory.getLogger(Report.class);

    @Override
    public String apply(String json, DLPContext context) {
        try {
            DocumentContext ctx = JsonPath.parse(json);

            if (context == null || !context.hasRiskReport()) {
                log.warn("No RiskReport provided. Returning unmodified JSON.");
                return ctx.jsonString();
            }

            String targetPath = getField() != null ? getField() : "$";

            Object target = ctx.read(targetPath);
            if (!(target instanceof Map)) {
                log.warn("Target path '{}' is not a JSON object. Cannot inject risk report.", targetPath);
                return ctx.jsonString();
            }

            ctx.put(targetPath, "risk", Map.of(
                    "category", context.getRiskReport().getCategory().name(),
                    "counts", context.getRiskReport().getStructuredReport()
            ));

            return ctx.jsonString();
        } catch (Exception e) {
            log.error("Failed to inject risk report at path '{}'", getField(), e);
            throw new RuntimeException("DLP Report injection failed at path: " + getField(), e);
        }
    }
}
