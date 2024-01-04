package com.predic8.membrane.core.interceptor.apikey.store;

import java.util.List;

public interface ApiKeyStore {

    List<String> getScopes(String apiKey);
}
