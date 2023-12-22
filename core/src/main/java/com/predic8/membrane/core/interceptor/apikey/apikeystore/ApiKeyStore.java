package com.predic8.membrane.core.interceptor.apikey.apikeystore;

import java.util.List;
import java.util.Map;

public interface ApiKeyStore {
    Map<String, List<String>> getScopes();
}
