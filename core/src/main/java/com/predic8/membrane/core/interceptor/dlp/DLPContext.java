package com.predic8.membrane.core.interceptor.dlp;

public class DLPContext {

    private final String body;
    private final RiskReport riskReport;

    public DLPContext(String body, RiskReport riskReport) {
        this.riskReport = riskReport;
        this.body = body;
    }

    public RiskReport getRiskReport() {
        return riskReport;
    }

    public boolean hasRiskReport() {
        return riskReport != null;
    }

    public String getBody() {
        return body;
    }
}
