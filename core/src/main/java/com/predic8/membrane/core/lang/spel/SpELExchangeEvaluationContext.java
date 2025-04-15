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
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.spel.functions.*;
import com.predic8.membrane.core.lang.spel.spelable.*;
import com.predic8.membrane.core.lang.spel.typeconverters.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.core.convert.support.*;
import org.springframework.expression.spel.support.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.URLParamUtil.DuplicateKeyOrInvalidFormStrategy.*;

public class SpELExchangeEvaluationContext extends StandardEvaluationContext {

    private static final Logger log = LoggerFactory.getLogger(SpELExchangeEvaluationContext.class);

    private static final ObjectMapper om = new ObjectMapper();

    private final Exchange exchange;
    private final Message message;
    private final SpELBody body; // Is used by SpEL in scripts

    // Avoid the common plural error
    private final SpELLablePropertyAware headers;

    // Avoid the common plural error
    private final SpELLablePropertyAware properties;

    private SpELLablePropertyAware params;

    private final SpELPathParameters pathParam;

    private String path;
    private String method;

    private int statusCode;

    private String scopes; // Is used by SpEL in scripts

    private SpELMessageWrapper request;
    private SpELMessageWrapper response;
    private final Flow flow;

    public SpELExchangeEvaluationContext(Exchange exchange, Flow flow) {
        super();

        this.exchange = exchange;
        this.flow = flow;
        this.message = exchange.getMessage(flow);
        this.body = new SpELBody(message);

        pathParam = new SpELPathParameters(exchange);
        properties = new SpELProperties(exchange.getProperties());
        headers = new SpELHeader(message.getHeader());

        extractFromRequest(exchange);
        extractFromResponse(exchange);

        setRootObject(this);
        addPropertyAccessor(new AwareExchangePropertyAccessor());

        System.out.println("getMethodResolvers() = " + getMethodResolvers());

//        List<MethodResolver> mr = getMethodResolvers();
//        mr.add(new BuiltInFunctionResolver());

        // Enables Membrane functions in SpEL scripts like 'hasScopes("admin")'
//        setMethodResolvers(List.of(new BuiltInFunctionResolver()));
        addMethodResolver(new BuiltInFunctionResolver());

        addTypeConverters();
    }

    private void addTypeConverters() {
        GenericConversionService cs = new DefaultConversionService();
        cs.addConverter(new SpELHeaderToStringTypeConverter());
        cs.addConverter(new SpELMapToStringTypeConverter());
        cs.addConverter(new SpELBodyToStringTypeConverter());
        setTypeConverter(new StandardTypeConverter(cs));
    }

    private void extractFromResponse(Exchange exchange) {
        Response response = exchange.getResponse();
        if (response == null)
            return;

        this.response = new SpELMessageWrapper(response);
        this.statusCode = response.getStatusCode();
    }

    private void extractFromRequest(Exchange exchange) {
        Request request = exchange.getRequest();
        if (request == null)
            return;

        path = request.getUri();
        method = request.getMethod();
        try {
            params = new SpELMap<>(URLParamUtil.getParams(new URIFactory(), exchange, ERROR));
        } catch (Exception e) {
            // Details are logged in URLParamUtil.getParams
            log.info("Error parsing query parameters");
        }
        this.request = new SpELMessageWrapper(exchange.getRequest());
    }

    public SpELLablePropertyAware getProperties() {
        return properties;
    }

    /**
     * Also get a property with property.fieldname
     */
    public SpELLablePropertyAware getProperty() {
        return properties;
    }

    public SpELLablePropertyAware getHeaders() {
        return headers;
    }

    /**
     * Also get headers with header.fieldname
     */
    public SpELLablePropertyAware getHeader() {
        return headers;
    }

    public SpELLablePropertyAware getParam() {
        return params;
    }

    public SpELLablePropertyAware getParams() {
        return params;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public Message getMessage() {
        return message;
    }

    public SpELBody getBody() { return body; }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public SpELMessageWrapper getRequest() {
        return request;
    }

    public SpELMessageWrapper getResponse() {
        return response;
    }

    public String getScopes() {
        return scopes;
    }

    public Flow getFlow() {
        return flow;
    }

    @SuppressWarnings("unused")
    public SpELLablePropertyAware getPathParam() {
        return pathParam;
    }

    public SpELMap<String, Object> getJson() throws IOException {
        return new SpELMap<String, Object>(om.readValue(message.getBodyAsStreamDecoded(), Map.class));
    }
}
