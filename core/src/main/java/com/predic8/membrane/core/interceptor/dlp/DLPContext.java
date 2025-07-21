package com.predic8.membrane.core.interceptor.dlp;

public record DLPContext(String body, RiskReport riskReport) {

    public boolean hasRiskReport() {
        return riskReport != null;
    }
}
