package com.predic8.membrane.core.lang.spel.functions;

public class BuildInFunctionException extends RuntimeException {

    private final String function;

    public BuildInFunctionException(String message, String function, Throwable cause) {
        super(message, cause);
        this.function = function;
    }

    public String getFunction() {
        return function;
    }
}
