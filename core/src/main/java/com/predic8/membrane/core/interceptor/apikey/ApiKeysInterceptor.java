package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apikey.apikeystore.ApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.util.Map.of;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.ofNullable;

@MCElement(name = "apiKey")
public class ApiKeysInterceptor extends AbstractInterceptor {
    private final Logger log = LoggerFactory.getLogger(ApiKeysInterceptor.class);

    public static final String SCOPES = "membrane-scopes";
    public static final String TYPE_4XX = "predic8.de/authorization/denied";
    public static final String TITLE_4XX = "Access Denied";
    private final List<ApiKeyStore> stores = new ArrayList<>();
    private final List<ApiKeyExtractor> extractors = new ArrayList<>();
    private boolean require = false;

    @Override
    public void init() {
        stores.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        var key = getKey(exc);

        if (require && key.isEmpty()) {
            return logErrorAndReturn(exc, 401, TYPE_4XX, TITLE_4XX, "Tried to access apiKey protected resource without key.");
        }

        if (key.isPresent()) {
            var scopes = getScopes(key.get());

            if (scopes.isEmpty()) {
                return logErrorAndReturn(exc, 403, TYPE_4XX, TITLE_4XX, "The provided API key is invalid or has no associated scopes.");
            }

            addScopes(exc, scopes);
        }

        return CONTINUE;
    }

    public void addScopes(Exchange exc, List<String> scopes) {
        exc.setProperty(SCOPES, scopes);
    }

    public Outcome logErrorAndReturn(Exchange exc, int statusCode, String type, String title, String info) {
        log.warn(info);
        exc.setResponse(createProblemDetails(statusCode, type, title, of("error", info)));
        return RETURN;
    }

    public List<String> getScopes(String key) {
        return stores.stream()
                     .flatMap(store -> store.getScopes(key).stream())
                     .collect(toList());
    }

    public Optional<String> getKey(Exchange exc) {
        return extractors.stream()
                         .flatMap(ext -> ofNullable(
                                 ext.extract(exc).orElse(null)
                         ))
                         .findFirst();
    }

    @MCAttribute
    void setRequire(boolean require) {
        this.require = require;
    }

    @MCChildElement(allowForeign = true)
    void setExtractors(List<ApiKeyExtractor> extractors) {
        this.extractors.addAll(extractors);
    }

    public List<ApiKeyExtractor> getExtractors() {
        return extractors;
    }
}
