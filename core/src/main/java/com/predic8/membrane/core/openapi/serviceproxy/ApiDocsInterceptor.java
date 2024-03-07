package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATH;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.toMap;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    private static final Pattern PATTERN_UI = Pattern.compile(PATH + "?/ui/(.*)");

    private Map<String, Map<String, OpenAPIRecord>> ruleApiSpecs;

    private static final Logger log = LoggerFactory.getLogger(OpenAPIPublisherInterceptor.class.getName());

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public void init(Router router) throws Exception {
        this.router = router;
        initializeRuleApiSpecs();
        super.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        var publisher = new OpenAPIPublisher(flattenApis(ruleApiSpecs));

        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return publisher.handleSwaggerUi(exc);
        }

        if (!exc.getRequest().getUri().startsWith("/api-doc"))
            return CONTINUE;


        return publisher.handleOverviewOpenAPIDoc(exc, router, log);
    }

    public static Map<String, OpenAPIRecord> flattenApis(Map<String, Map<String, OpenAPIRecord>> ruleApiSpecs) {
        return ruleApiSpecs.values()
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (value1, value2) -> value1
                ));
    }

    private void initializeRuleApiSpecs() {
        ruleApiSpecs = new HashMap<>();

        router.getRuleManager().getRules().stream()
                .filter(this::hasOpenAPIInterceptor)
                .forEach(rule -> {
                    OpenAPIInterceptor interceptor = getOpenAPIInterceptor(rule);
                    if (interceptor != null) { // TODO Mit Optional und Flatmap
                        ruleApiSpecs.put(rule.getName(), interceptor.getApiProxy().apiRecords);
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

    private boolean acceptsHtmlExplicit(Exchange exc) {
        if (exc.getRequest().getHeader().getAccept() == null)
            return false;
        return exc.getRequest().getHeader().getAccept().contains("html");
    }
}
