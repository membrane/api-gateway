package com.predic8.membrane.core.interceptor.json;

public class JsonProtectionException extends Exception{
    private final String message;
    private final int line;
    private final int col;

    public JsonProtectionException(String msg, int line, int col) {
        this.message = msg;
        this.line = line;
        this.col = col;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }
}
