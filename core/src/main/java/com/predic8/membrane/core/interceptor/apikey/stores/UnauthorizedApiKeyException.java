package com.predic8.membrane.core.interceptor.apikey.stores;

public class UnauthorizedApiKeyException extends Exception {
    public UnauthorizedApiKeyException() {
        super("The provided API key is invalid or not authorized to access the resource.");
    }
}
