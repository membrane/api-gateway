package com.predic8.membrane.core.interceptor.swagger;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.resolver.ResourceRetrievalException;

public interface ApiKeyValidator {
    boolean isValid(String key);

    void init(Router router) throws ResourceRetrievalException, Exception;
}
