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

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATH;
import static java.lang.String.valueOf;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toMap;

@MCElement(name = "apiDocs")
public class ApiDocsInterceptor extends AbstractInterceptor {

    private static final Pattern PATTERN_UI = Pattern.compile(PATH + "?/ui/(.*)");

    Map<String, Map<String, OpenAPIRecord>> ruleApiSpecs;

    private static final Logger log = LoggerFactory.getLogger(ApiDocsInterceptor.class.getName());

    private boolean initialized = false;

    public ApiDocsInterceptor() {
        name = "Api Docs";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if(!initialized) {
            initializeRuleApiSpecs();
            initialized = true;
        }
        var publisher = new OpenAPIPublisher(flattenApis(ruleApiSpecs));

        if (exc.getRequest().getUri().matches(valueOf(PATTERN_UI))) {
            return publisher.handleSwaggerUi(exc);
        }

        if (!exc.getRequest().getUri().startsWith("/api-doc"))
            return CONTINUE;


        return publisher.handleOverviewOpenAPIDoc(exc, router, log);
    }

    // Untersuchen
    public static Map<String, OpenAPIRecord> flattenApis(Map<String, Map<String, OpenAPIRecord>> ruleApiSpecs) {
        return ruleApiSpecs.values()
                .stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (entry, ignored) -> entry
                ));
    }

    // No void return value instead.
    void initializeRuleApiSpecs() {
        ruleApiSpecs = new HashMap<>();

        router.getRuleManager().getRules().stream()
                .filter(this::hasOpenAPIInterceptor)

                // Functional?
                .forEach(rule -> getOpenAPIInterceptor(rule).ifPresent(openAPIInterceptor -> ruleApiSpecs.put(
                        rule.getName(),
                        openAPIInterceptor.getApiProxy().apiRecords)
                ));
    }

    private boolean hasOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream().anyMatch(ic -> ic instanceof OpenAPIInterceptor);
    }

    Optional<OpenAPIInterceptor> getOpenAPIInterceptor(Rule rule) {
        Optional<Interceptor> i = rule.getInterceptors().stream()
                .filter(ic -> ic instanceof OpenAPIInterceptor)
                .findFirst();

        if (i.isEmpty())
            return empty();

        if (i.get() instanceof OpenAPIInterceptor oasInterceptor) {
            return Optional.of(oasInterceptor);
        }
        throw new RuntimeException("Should not happen!");
    }

    private boolean acceptsHtmlExplicit(Exchange exc) {
        if (exc.getRequest().getHeader().getAccept() == null)
            return false;
        return exc.getRequest().getHeader().getAccept().contains("html");
    }
}
