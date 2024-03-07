package com.predic8.membrane.core.interceptor.apidocs;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;

import java.util.HashMap;
import java.util.Map;

@MCElement(name = "apiRegistry")
public class ApiRegistry {

    private HashMap<String, OpenAPIRecord> apis = new HashMap<>();

    public void addApi(Map<String, OpenAPIRecord> apiRecords) {
        this.apis.putAll(apiRecords);
    }

    public HashMap<String, OpenAPIRecord> getApis() {
        return apis;
    }
}
