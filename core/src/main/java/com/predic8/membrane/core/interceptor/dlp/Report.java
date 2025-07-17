package com.predic8.membrane.core.interceptor.dlp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
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

            String targetPath = getField();
            if (targetPath == null) {
                log.warn("No field set on <report>. Skipping.");
                return ctx.jsonString();
            }

            Object value = ctx.read(targetPath);
            String normalizedFieldName = targetPath.replaceFirst("^\\$\\.", "").toLowerCase(Locale.ROOT);
            String riskLevel = context.getRiskReport()
                    .getMatchedFields()
                    .getOrDefault(normalizedFieldName, "UNCLASSIFIED");

            Map<String, Object> riskBlock = Map.of(
                    "value", value,
                    "risk", Map.of(
                            "field", normalizedFieldName,
                            "category", riskLevel
                    )
            );

            ctx.set(targetPath, riskBlock);
            return ctx.jsonString();

        } catch (Exception e) {
            log.error("Failed to inject risk metadata into field '{}'", getField(), e);
            throw new RuntimeException("DLP Report injection failed at path: " + getField(), e);
        }
    }
}
