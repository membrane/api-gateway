/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
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
                .peek(this::setSpecRewrites)
                .flatMap(this::getRecordEntryStreamOrEmpty)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (key1, ignored) -> key1, // If duplicate keys, keep the first one
                        LinkedHashMap::new
                ));
    }

    protected void setSpecRewrites(Rule rule) {
        var key = rule.getKey();
        //noinspection OptionalGetWithoutIsPresent
        getOpenAPIInterceptor(rule).get().getApiProxy().getSpecs().forEach(spec -> {
            if (spec.getRewrite() != null) {
                setIfNull(spec.getRewrite(), Rewrite::getPort, Rewrite::setPort, key.getPort());
                setIfNull(spec.getRewrite(), Rewrite::getHost, Rewrite::setHost, key.getHost());
                setIfNull(spec.getRewrite(), Rewrite::getBasePath, Rewrite::setBasePath, key.getPath());
            } else {
                spec.setRewrite(new Rewrite() {{
                    setHost(key.getHost());
                    setPort(key.getPort());
                    setBasePath(key.getPath());
                }});
            }
        });
    }

    private Stream<Map.Entry<String, OpenAPIRecord>> getRecordEntryStreamOrEmpty(Rule rule) {
        return getOpenAPIInterceptor(rule)
                .map(ApiDocsInterceptor::getRecordEntryStream)
                .orElse(Stream.empty());
    }

    private static Stream<Map.Entry<String, OpenAPIRecord>> getRecordEntryStream(OpenAPIInterceptor oai) {
        return oai.getApiProxy().apiRecords.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()));
    }

    private boolean hasOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream().anyMatch(ic -> ic instanceof OpenAPIInterceptor);
    }

    static Optional<OpenAPIInterceptor> getOpenAPIInterceptor(Rule rule) {
        return rule.getInterceptors().stream()
                .filter(ic -> ic instanceof OpenAPIInterceptor)
                .map(ic -> (OpenAPIInterceptor) ic) // Previous line checks type, so cast should be fine
                .findFirst();
    }

    @Override
    public String getDisplayName() {
        return "Api Docs";
    }

    @Override
    public String getShortDescription() {
        return "Displays all deployed APIs";
    }

    @Override
    public String getLongDescription() {
        ruleApiSpecs = initializeRuleApiSpecs();
        initialized = true;
        StringBuilder sb = new StringBuilder();

        sb.append("<table>");
        sb.append("<thead><th>API</th><th>OpenAPI Version</th></thead>");

        for (String ruleKey : ruleApiSpecs.keySet()) {
            OpenAPIRecord apiSpec = ruleApiSpecs.get(ruleKey);
            sb.append("<tr>");
            sb.append("<td>");
            sb.append(ruleKey);
            sb.append("</td>");
            sb.append("<td>");
            sb.append(apiSpec.version);
            sb.append("</td>");
            sb.append("</tr>");
        }

        sb.append("</table>");

        return sb.toString();
    }

    public static <T, U> void setIfNull(T rewrite, Function<T, U> getter, BiConsumer<T, U> setter, U defaultValue) {
        if(getter.apply(rewrite) != null) return;
        if(!defaultValue.equals("*")) {
            setter.accept(rewrite, defaultValue);
        }
    }
}