package com.predic8.membrane.core.interceptor.apikey.stores;

import java.util.List;

public interface ApiKeyStore {
    List<String> getScopes(String apiKey);
}
