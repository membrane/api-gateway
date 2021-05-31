package com.predic8.membrane.core.interceptor.kubernetes.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseStatus {
    private int code;
    private String message;

    public ResponseStatus() {}
    public ResponseStatus(String message) {
        this(403, message);
    }
    public ResponseStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
