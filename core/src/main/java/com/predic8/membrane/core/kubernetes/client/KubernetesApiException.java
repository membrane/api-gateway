package com.predic8.membrane.core.kubernetes.client;

import java.util.Map;

public class KubernetesApiException extends Exception {

    public static final String ALREADY_EXISTS = "AlreadyExists";

    private final int code;
    private final Map result;
    private final String reason;

    public KubernetesApiException(int code, Map result) {
        super(code + " " + result.toString());
        this.code = code;
        this.result = result;
        reason = (String) result.get("reason");
    }

    public int getCode() {
        return code;
    }

    public Map getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }
}
