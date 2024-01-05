package com.predic8.membrane.core.interceptor.apikey.stores;

public class UnauthorizedKeyException extends Exception {
    public UnauthorizedKeyException() {
        super("The provided API key is invalid or not authorized to access the resource.");
    }
}
