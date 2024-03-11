package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPIPublisher.PATH;
import static java.lang.String.valueOf;

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

        return publisher.handleOverviewOpenAPIDoc(exc, router, log);
    }

    public Map<String, OpenAPIRecord> initializeRuleApiSpecs() {
        return router.getRuleManager().getRules().stream()
                .filter(this::hasOpenAPIInterceptor)
                .flatMap(rule -> getOpenAPIInterceptor(rule)
                        .map(ApiDocsInterceptor::getRecordEntryStream)
                        .orElse(Stream.empty()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (key1, key2) -> key1, // If duplicate keys, keep the first one
                        LinkedHashMap::new
                ));
    }

    private static Stream<Map.Entry<String, OpenAPIRecord>> getRecordEntryStream(OpenAPIInterceptor oai) {
        return oai.getApiProxy().apiRecords.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()));
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
