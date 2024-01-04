package com.predic8.membrane.core.interceptor.apikey;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apikey.store.ApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.extractors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;


/**
 *
 * @TODO: Write ApiKeyQueryStringExtractor
 */
@SuppressWarnings("unused")
@MCElement(name = "apiKey")
public class ApiKeysInterceptor extends AbstractInterceptor {
    private final Logger log = LoggerFactory.getLogger(ApiKeysInterceptor.class);

    public static final String SCOPES = "membrane-scopes";
    private final List<ApiKeyStore> store = new ArrayList<>();
    private final List<ApiKeyExtractor> extractors = new ArrayList<>();

    private boolean require = false;

    @Override
    public void init() {
        store.addAll(router.getBeanFactory().getBeansOfType(ApiKeyStore.class).values());
       // stores.stream().map(ApiKeyStore::getScopes).forEach(scopes::putAll);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {

        var key = getKey(exc);

        if (key.isEmpty()) {
            if (require) {
                log.warn("Tried to access apiKey protected resource without key.");
                exc.setResponse(createProblemDetails(401, "predic8.de/authorization/denied", "Access Denied"));
                return RETURN;
            }
            return CONTINUE;
        }


        var scs = store.stream().map(apiKeyStore -> apiKeyStore.getScopes(key.get())).toList();
        System.out.println("scs = " + scs);

        // exc.setProperty(SCOPES, store.getScopes(key.get()));

        return CONTINUE;
    }

    // @TODO Test
    public Optional<String> getKey(Exchange exc) {
        // Map calls for every Extractor. It should be called only once.
        return extractors.stream().map(extractor -> extractor.extract(exc)).filter(Optional::isPresent).findFirst().get();
    }

    @MCAttribute
    public void setRequire(boolean require) {
        this.require = require;
    }


    @MCChildElement(allowForeign = true)
    public void setExtractors(List<ApiKeyExtractor> extractors) {
        this.extractors.addAll(extractors);
    }

    /**
     * Method is needed even if it is not called!
     */
    public List<ApiKeyExtractor> getExtractors() {
        return extractors;
    }
}
