package com.predic8.membrane.core.interceptor.apidocs;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyStore;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecordFactory;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@MCElement(name = "apiRegistry")
public class ApiRegistry {

    private HashMap<String, OpenAPIRecord> apis = new HashMap<>();
    private List<OpenAPISpec> specs = new ArrayList();

    private void resolveApis(Router router) throws IOException {
        var oarf = new OpenAPIRecordFactory(router);
        apis = (HashMap<String, OpenAPIRecord>) oarf.create(specs);
    }

    public HashMap<String, OpenAPIRecord> getApis() {
        return apis;
    }

    @MCChildElement(allowForeign = true)
    public void setSpecs(List<OpenAPISpec> specs) {
        this.specs.addAll(specs);
    }

    public List<OpenAPISpec> getSpecs() {
        return specs;
    }
}
