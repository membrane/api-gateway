package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATH;
import static java.lang.String.valueOf;
import static java.util.Optional.empty;
import java.util.LinkedHashMap;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    private static final Pattern PATTERN_UI = Pattern.compile(PATH + "?/ui/(.*)");

    Map<String, OpenAPIRecord>  ruleApiSpecs;

    private static final Logger log = LoggerFactory.getLogger(ApiDocsInterceptor.class.getName());

    private boolean initialized = false;

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(!initialized) {
            ruleApiSpecs = initializeRuleApiSpecs();
            initialized = true;
        }
        var publisher = new OpenAPIPublisher(ruleApiSpecs);

        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return publisher.handleSwaggerUi(exc);
        }

        if (!exc.getRequest().getUri().startsWith("/api-doc"))
            return CONTINUE;


        return publisher.handleOverviewOpenAPIDoc(exc, router, log);
    }

    public Map<String, OpenAPIRecord> initializeRuleApiSpecs() {
        return router.getRuleManager().getRules().stream()
                .filter(this::hasOpenAPIInterceptor)
                .flatMap(rule -> getOpenAPIInterceptor(rule)
                        .map(openAPIInterceptor -> openAPIInterceptor.getApiProxy().apiRecords.entrySet().stream()
                                .map(entry -> Map.entry(entry.getKey(), entry.getValue())))
                        .orElse(Stream.empty()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (key1, key2) -> key1, // If duplicate keys, keep the first one
                        LinkedHashMap::new
                ));
    }

    private boolean hasOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream().anyMatch(ic -> ic instanceof OpenAPIInterceptor);
    }

    Optional<OpenAPIInterceptor> getOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream()
                .filter(ic -> ic instanceof OpenAPIInterceptor)
                .map(ic -> (OpenAPIInterceptor) ic)
                .findFirst();
    }

    private boolean acceptsHtmlExplicit(Exchange exc) {
        if (exc.getRequest().getHeader().getAccept() == null)
            return false;
        return exc.getRequest().getHeader().getAccept().contains("html");
    }
}
