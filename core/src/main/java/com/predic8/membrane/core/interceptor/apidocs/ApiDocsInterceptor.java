package com.predic8.membrane.core.interceptor.apidocs;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPIInterceptor;

import java.util.List;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    private List<Interceptor> apis;
    private boolean initialized = false;

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (!initialized) {
            this.apis = getApis();
            initialized = true;
        }

        System.out.println(apis);
        return CONTINUE;
    }

    private List<Interceptor> getApis() {
        var i = router.getRuleManager().getRules().stream()
                .flatMap(rule -> rule.getInterceptors().stream())
                .filter(ic -> {
                    boolean b = ic instanceof OpenAPIInterceptor;
                });

        return
    }
}
