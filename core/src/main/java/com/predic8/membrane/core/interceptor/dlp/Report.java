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
            log.warn("No RiskReport provided. Skipping report logging.");
            return Objects.requireNonNull(context).getBody();
        }
        RiskReport report = context.getRiskReport();
        log.info("DLP Risk Summary: Category={}, Counts={}",
                report.getCategory(), report.getRiskCounts());
        if (!report.getMatchedFields().isEmpty()) {
            StringBuilder sb = new StringBuilder("Matched fields:");
            report.getMatchedFields().forEach((field, category) ->
                    sb.append("\n - ").append(field).append(" -> ").append(category));
            log.info(sb.toString());
        }
        return context.getBody();
    }

}
