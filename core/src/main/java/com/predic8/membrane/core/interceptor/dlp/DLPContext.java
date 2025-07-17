package com.predic8.membrane.core.interceptor.dlp;

public class DLPContext {

    private final RiskReport riskReport;

    public DLPContext(RiskReport riskReport) {
        this.riskReport = riskReport;
    }

    public RiskReport getRiskReport() {
        return riskReport;
    }

    public boolean hasRiskReport() {
        return riskReport != null;
    }
}
