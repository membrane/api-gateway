package com.predic8.membrane.core.interceptor.apikey;


import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.apikey.apikeystore.ApiKeyStore;
import com.predic8.membrane.core.interceptor.apikey.extractors.ApiKeyExtractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;

@MCElement(name = "ApiKeyInterceptor") // @TODO rename to <apiKey>
public class ApiKeysInterceptor extends AbstractInterceptor {

    public static final String SCOPES = "membrane-scopes";
    private List<ApiKeyStore> stores;
    private ApiKeyExtractor extractor;
    private Map<String, List<String>> scopes = new HashMap<>();
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
                // @TODO set ProblemJSON 401
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
