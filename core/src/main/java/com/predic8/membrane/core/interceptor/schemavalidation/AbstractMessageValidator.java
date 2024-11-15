package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.http.*;

public abstract class AbstractMessageValidator implements MessageValidator {

    public static String REQUEST = "request";
    public static String RESPONSE = "response";
    public static String UNKNOWN = "unknown";

    protected String getSourceOfError(Message msg) {
        if (msg instanceof Request)
            return REQUEST;
        if (msg instanceof Response)
            return RESPONSE;
        return UNKNOWN;
    }
}
