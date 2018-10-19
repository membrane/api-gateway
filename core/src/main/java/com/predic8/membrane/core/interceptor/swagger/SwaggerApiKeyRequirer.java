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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Body;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;

@MCElement(name="swaggerApiKeyRequirer")
public class SwaggerApiKeyRequirer extends AbstractInterceptor {

    private ApiKeyTransmissionStrategy apiKeyTransmissionStrategy;

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        if (exc.getRequest().getUri().endsWith("api-docs")) {
            String s = exc.getResponse().getBodyAsStringDecoded();

            String insert = "\"security\":[{" +
                    "\"APIKeyHeader\": []}]," +

                    "\"securityDefinitions\":{" +
                    "   \"APIKeyHeader\": "+apiKeyTransmissionStrategy.getSwaggerDescriptionJson()+"  },";

            s = "{" + insert +  s.substring(1);

            exc.getResponse().setBodyContent(s.getBytes("UTF-8"));
            return Outcome.RETURN;
        }
        return super.handleResponse(exc);
    }

    public ApiKeyTransmissionStrategy getApiKeyTransmissionStrategy() {
        return apiKeyTransmissionStrategy;
    }

    @MCChildElement
    public void setApiKeyTransmissionStrategy(ApiKeyTransmissionStrategy apiKeyTransmissionStrategy) {
        this.apiKeyTransmissionStrategy = apiKeyTransmissionStrategy;
    }
}
