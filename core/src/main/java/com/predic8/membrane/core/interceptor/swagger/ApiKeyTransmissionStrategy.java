package com.predic8.membrane.core.interceptor.swagger;

import com.predic8.membrane.core.http.Request;

public interface ApiKeyTransmissionStrategy {
    String getApiKey(Request request);

    String getSwaggerDescriptionJson();
}
