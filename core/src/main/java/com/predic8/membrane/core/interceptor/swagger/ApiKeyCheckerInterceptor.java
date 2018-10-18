/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

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
