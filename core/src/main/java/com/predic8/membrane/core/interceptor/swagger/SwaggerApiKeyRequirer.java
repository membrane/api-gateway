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
