package com.predic8.membrane.core.interceptor.apikey;


import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
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

public class ApiKeysInterceptor extends AbstractInterceptor {

    private static final String SCOPES = "scopes";
    private List<ApiKeyStore> apiKeyStores;
    private ApiKeyExtractor apiKey;
    private Map<String, List<String>> scopes = new HashMap<>();
    private boolean requireKey = false;


    @Override
    public void init() {
        apiKeyStores.stream().map(ApiKeyStore::getScopes).forEach(scopes::putAll);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        final String key = apiKey.extract(exc);

        if (key == null && requireKey)
            return RETURN;

        if (scopes.containsKey(key)) {
            exc.setProperty(SCOPES, scopes.get(key));
        }

        return CONTINUE;
    }

    @MCAttribute
    void setRequireKey(boolean requireKey) {
        this.requireKey = requireKey;
    }

    @MCChildElement
    void setApiKeyStores(List<ApiKeyStore> apiKeyStores) {
        this.apiKeyStores = apiKeyStores;
    }

    @MCChildElement
    void setApiKey(ApiKeyExtractor apiKey) {
        this.apiKey = apiKey;
    }

}
