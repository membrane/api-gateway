package com.predic8.membrane.core.interceptor.apikey.stores;

import java.util.List;
import java.util.Optional;

public interface ApiKeyStore {
    Optional<List<String>> getScopes(String apiKey);
}
