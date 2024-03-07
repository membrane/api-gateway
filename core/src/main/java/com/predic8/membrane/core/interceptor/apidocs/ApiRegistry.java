package com.predic8.membrane.core.interceptor.apidocs;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.interceptor.apikey.stores.ApiKeyStore;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecord;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIRecordFactory;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@MCElement(name = "apiRegistry")
public class ApiRegistry implements ApplicationListener<ContextStartedEvent> {

    private HashMap<String, OpenAPIRecord> apis = new HashMap<>();
    private List<OpenAPISpec> specs = new ArrayList();

    @Override
    public void onApplicationEvent(ContextStartedEvent ignored) {
        apis = resolveApis(specs);
    }

    private HashMap<String, OpenAPIRecord> resolveApis(List<OpenAPISpec> s) {
        return s.stream().map(sp -> {
            var oarf = new OpenAPIRecordFactory();
        }).collect(Collectors.toMap());
    }

    @MCChildElement(allowForeign = true)
    public void setSpecs(List<OpenAPISpec> specs) {
        this.specs.addAll(specs);
    }

    public List<OpenAPISpec> getSpecs() {
        return specs;
    }
}
