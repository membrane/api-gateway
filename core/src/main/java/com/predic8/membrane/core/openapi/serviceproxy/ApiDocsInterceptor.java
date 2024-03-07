package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    private Map<String, List<OpenAPISpec>> ruleApiSpecs;
    private boolean initialized = false;

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public void init(Router router) throws Exception {
        System.out.println("ApiDocsInterceptor.init");
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!initialized) {
            initializeRuleApiSpecs();
            initialized = true;
        }

        System.out.println(ruleApiSpecs);
        return CONTINUE;
    }

    private void initializeRuleApiSpecs() {
        ruleApiSpecs = new HashMap<>();

        router.getRuleManager().getRules().stream()
                .filter(this::hasOpenAPIInterceptor)
                .forEach(rule -> {
                    OpenAPIInterceptor interceptor = getOpenAPIInterceptor(rule);
                    if (interceptor != null) { // Mit Optional und Flatmap
                        ruleApiSpecs.put(rule.getName(), interceptor.getApiProxy().getSpecs());
                    }
                });
    }

    private boolean hasOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream().anyMatch(ic -> ic instanceof OpenAPIInterceptor);
    }

    private OpenAPIInterceptor getOpenAPIInterceptor(Rule rule) {
        return (OpenAPIInterceptor) rule.getInterceptors().stream()
                .filter(ic -> ic instanceof OpenAPIInterceptor)
                .findFirst().orElse(null);
    }
}
