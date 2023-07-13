package com.predic8.membrane.core.interceptor.json;

public class JsonProtectionException extends Exception{
    private final String message;

    public JsonProtectionException(String msg) {
        this.message = msg;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
