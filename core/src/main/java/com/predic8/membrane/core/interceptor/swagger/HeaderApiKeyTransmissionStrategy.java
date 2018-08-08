package com.predic8.membrane.core.interceptor.swagger;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Request;

@MCElement(name = "header", topLevel = false)
public class HeaderApiKeyTransmissionStrategy implements ApiKeyTransmissionStrategy {

    private String name;


    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getApiKey(Request request) {
        return request.getHeader().getFirstValue(name);
    }

    @Override
    public String getSwaggerDescriptionJson() {
        return "{\"type\": \"apiKey\",\"in\": \"header\",\"name\": \""+name+"\"  }";
    }
}
