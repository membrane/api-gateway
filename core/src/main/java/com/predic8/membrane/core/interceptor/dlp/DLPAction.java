package com.predic8.membrane.core.interceptor.dlp;

public interface DLPAction {
    String apply(String body, DLPContext context);
}
