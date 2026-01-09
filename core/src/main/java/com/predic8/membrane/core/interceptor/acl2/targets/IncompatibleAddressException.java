package com.predic8.membrane.core.interceptor.acl2.targets;

public class IncompatibleAddressException extends Exception {

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
