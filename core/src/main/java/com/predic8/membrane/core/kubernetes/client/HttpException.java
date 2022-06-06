package com.predic8.membrane.core.kubernetes.client;

public class HttpException extends Exception {
    public HttpException(int code, String message) {
        super(code + " " + message);
    }


}
