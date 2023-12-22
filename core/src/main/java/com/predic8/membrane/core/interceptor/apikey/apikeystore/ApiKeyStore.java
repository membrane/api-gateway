package com.predic8.membrane.core.interceptor.apikey.apikeystore;

import java.util.List;

public interface ApiKeyStore {
    List<String> getScopes(String key);
}
