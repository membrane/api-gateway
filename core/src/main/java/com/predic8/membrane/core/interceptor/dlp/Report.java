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
    public String apply(String json) {
        try {
            DocumentContext context = JsonPath.parse(json);
            return context.jsonString();
        } catch (Exception e) {
            log.error("Failed to inject DLP report", e);
            throw new RuntimeException("DLP Report injection failed", e);
        }
    }

    public String apply(String json, RiskReport risk) {
        try {
            DocumentContext context = JsonPath.parse(json);
            context.put("$", "risk", Map.of("category", risk.getCategory().name(), "counts", risk.getLogReport()));
            return context.jsonString();
        } catch (Exception e) {
            log.error("Failed to inject DLP report", e);
            throw new RuntimeException("DLP Report injection failed", e);
        }
    }
}
