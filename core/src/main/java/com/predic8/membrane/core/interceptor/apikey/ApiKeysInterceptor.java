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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

@MCElement(name = "apiKey")
public class ApiKeysInterceptor extends AbstractInterceptor {
    private final Logger log = LoggerFactory.getLogger(ApiKeysInterceptor.class);

    public static final String SCOPES = "membrane-scopes";
    private List<ApiKeyStore> stores;
    private ApiKeyExtractor extractor;
    private final Map<String, List<String>> scopes = new HashMap<>();
    private boolean require = false;

    @Override
    public void init() {
        stores.stream().map(ApiKeyStore::getScopes).forEach(scopes::putAll);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        var key = extractor.extract(exc);

        if (key.isEmpty()) {
            if (require) {
                log.warn("Tried to access apiKey protected resource without key.");
                exc.setResponse(createProblemDetails(401, "predic8.de/authorization/denied", "Access Denied"));
                return RETURN;
            }
            return CONTINUE;
        }

        if (scopes.containsKey(key.get())) {
            exc.setProperty(SCOPES, scopes.get(key.get()));
        }

        return CONTINUE;
    }

    @MCAttribute
    void setRequire(boolean require) {
        this.require = require;
    }

    @MCChildElement(allowForeign = true, order = 0)
    void setStores(List<ApiKeyStore> stores) {
        this.stores = stores;
    }

    @MCChildElement(allowForeign = true, order = 1)
    void setExtractor(ApiKeyExtractor extractor) {
        this.extractor = extractor;
    }

}
