package com.predic8.membrane.core.interceptor.swagger;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.util.URLUtil;

@MCElement(name = "apiKeyChecker")
public class ApiKeyCheckerInterceptor extends AbstractInterceptor {

    boolean allowSwagger;

    ApiKeyValidator apiKeyValidator;
    ApiKeyTransmissionStrategy apiKeyTransmissionStrategy;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        apiKeyValidator.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if (allowSwagger) {
            String path = exc.getRequest().getUri();

            if (exc.getRule() instanceof ServiceProxy) {
                Path path1 = ((ServiceProxy) exc.getRule()).getPath();
                if (path1 != null) {
                    if (!path1.isRegExp())
                        if (path.startsWith(path1.getValue()))
                            path = path.substring(path1.getValue().length());
                }
            }

            if (path.equals("/swagger-ui.html")
            || path.startsWith("/swagger-resources")
            || path.startsWith("/webjars/springfox-swagger-ui")
            || path.equals("/v2/api-docs"))
                return Outcome.CONTINUE;
        }

        String currentKey = apiKeyTransmissionStrategy.getApiKey(exc.getRequest());
        if (currentKey == null || !apiKeyValidator.isValid(currentKey)) {
            exc.setResponse(Response.unauthorized("Api Key is missing").build());
            return Outcome.RETURN;
        }

        return super.handleRequest(exc);
    }

    public ApiKeyValidator getApiKeyValidator() {
        return apiKeyValidator;
    }

    @MCChildElement(order=10)
    public void setApiKeyValidator(ApiKeyValidator apiKeyValidator) {
        this.apiKeyValidator = apiKeyValidator;
    }

    public ApiKeyTransmissionStrategy getApiKeyTransmissionStrategy() {
        return apiKeyTransmissionStrategy;
    }

    @MCChildElement(order=20)
    public void setApiKeyTransmissionStrategy(ApiKeyTransmissionStrategy apiKeyTransmissionStrategy) {
        this.apiKeyTransmissionStrategy = apiKeyTransmissionStrategy;
    }

    public boolean isAllowSwagger() {
        return allowSwagger;
    }

    @MCAttribute
    public void setAllowSwagger(boolean allowSwagger) {
        this.allowSwagger = allowSwagger;
    }
}
