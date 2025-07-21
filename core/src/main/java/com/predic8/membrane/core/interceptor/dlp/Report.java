package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@MCElement(name = "report")
public class Report extends Action {

    private static final Logger log = LoggerFactory.getLogger(Report.class);

    @Override
    public String apply(DLPContext context) {
        if (context == null || !context.hasRiskReport()) {
            log.warn("No RiskReport provided. Skipping field report.");
            return Objects.requireNonNull(context).body();
        }

        String path = getField(); // e.g. $.first_name
        if (path == null || path.isBlank()) {
            log.warn("No field specified in <report />. Skipping.");
            return context.body();
        }

        try {
            String normalized = path.replaceFirst("^\\$\\.", "");

            String riskLevel = context.riskReport()
                    .getMatchedFields()
                    .getOrDefault(normalized, "UNCLASSIFIED");

            String category = context.riskReport()
                    .getCategoryOf(normalized);

            log.info("[Report]: Field='{}' | Category='{}' | Risk Level='{}'",
                    normalized, category, riskLevel);

        } catch (Exception e) {
            log.warn("Could not log field '{}': {}", getField(), e.getMessage());
        }

        return context.body();
    }
}
