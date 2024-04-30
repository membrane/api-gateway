/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang.spel;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.lang.spel.functions.BuildInFunctionResolver;
import com.predic8.membrane.core.lang.spel.spelable.*;
import com.predic8.membrane.core.util.URIFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.support.*;
import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.ERROR;
import static com.predic8.membrane.core.util.URLParamUtil.getParams;

public class ExchangeEvaluationContext extends StandardEvaluationContext {
    private static final Logger log = LoggerFactory.getLogger(ExchangeEvaluationContext.class);

    private static  final ObjectMapper om = new ObjectMapper();

    private final Exchange exchange;
    private final Message message;
    private final SpELLablePropertyAware headers;
    private final SpELLablePropertyAware properties;
    private String path;
    private String method;
    private Map<String, String> params;
    private int statusCode;

    private SpELMessageWrapper request;
    private SpELMessageWrapper response;

    public ExchangeEvaluationContext(Exchange exc) {
        this(exc, exc.getRequest());
    }

    public ExchangeEvaluationContext(Exchange exc, Message message) {
        super();

        this.message = message;
        exchange = exc;
        properties = new SpELProperties(exc.getProperties());
        headers = new SpELHeader(message.getHeader());

        Request request = exc.getRequest();
        if (request != null) {
            path = request.getUri();
            method = request.getMethod();
            try {
                params = getParams(new URIFactory(), exc, ERROR);
            } catch (Exception e) {
                log.warn("Error parsing query parameters", e);
            }
            this.request = new SpELMessageWrapper(exc.getRequest());
        }

        Response response = exc.getResponse();
        if (response != null) {
            this.response = new SpELMessageWrapper(exc.getResponse());
            this.statusCode = exc.getResponse().getStatusCode();
        }

        setRootObject(this);
        addPropertyAccessor(new AwareExchangePropertyAccessor());

        // Enables Membrane functions in SpEL scripts like 'hasScopes("admin")'
        setMethodResolvers(List.of(new BuildInFunctionResolver()));
    }


    public SpELLablePropertyAware getProperties() {
        return properties;
    }

    public SpELLablePropertyAware getHeaders() {
        return headers;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public Message getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public int getStatusCode() { return statusCode; }

    public SpELMessageWrapper getRequest() {
        return request;
    }

    public SpELMessageWrapper getResponse() {
        return response;
    }

    public SpELMap<String, Object> getJson() throws IOException {
        return new SpELMap<String, Object>(om.readValue(message.getBodyAsStreamDecoded(), Map.class));
    }
}
