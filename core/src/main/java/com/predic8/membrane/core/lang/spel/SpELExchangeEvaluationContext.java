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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.config.xml.XmlConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderMap;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.LazyBody;
import com.predic8.membrane.core.lang.spel.functions.BuiltInFunctionResolver;
import com.predic8.membrane.core.lang.spel.spelable.*;
import com.predic8.membrane.core.lang.spel.typeconverters.ListToStringTypeConverter;
import com.predic8.membrane.core.lang.spel.typeconverters.SpELBodyToStringTypeConverter;
import com.predic8.membrane.core.lang.spel.typeconverters.SpELMapToStringTypeConverter;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.core.util.URLParamUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;

import java.io.IOException;
import java.util.Map;

import static com.predic8.membrane.annot.Constants.VERSION;

public class SpELExchangeEvaluationContext extends StandardEvaluationContext {

    private static final Logger log = LoggerFactory.getLogger(SpELExchangeEvaluationContext.class);

    private static final ObjectMapper om = new ObjectMapper();

    private final Exchange exchange;
    private final Message message;
    private final LazyBody body; // Is used by SpEL in scripts

    // Avoid the common plural error
    private final SpELLablePropertyAware headers;

    private final SpELLablePropertyAware cookies;

    // Avoid the common plural error
    private final SpELLablePropertyAware properties;

    private SpELLablePropertyAware params;

    private final SpELLablePropertyAware gateway;

    private final SpELPathParameters pathParam;

    private String path;
    private String method;

    private int statusCode;

    private String scopes; // Is used by SpEL in scripts

    private SpELMessageWrapper request;
    private SpELMessageWrapper response;
    private final Flow flow;

    public SpELExchangeEvaluationContext(Exchange exchange, Flow flow, Router router) {
        this(exchange, flow, router, null);
    }

    public SpELExchangeEvaluationContext(Exchange exchange, Flow flow, Router router, XmlConfig localXmlConfig) {
        super();

        this.exchange = exchange;
        this.flow = flow;
        this.message = exchange.getMessage(flow);
        this.body = new LazyBody(message);

        pathParam = new SpELPathParameters(exchange);
        properties = new SpELProperties(exchange.getProperties());
        headers = new SpELMap<>(new HeaderMap(message.getHeader()));
        cookies = new SpELCookie(message.getHeader());
        gateway = new SpELMap<>(Map.of("version", VERSION));

        extractFromRequest(exchange);
        extractFromResponse(exchange);

        setRootObject(this);
        addPropertyAccessor(new AwareExchangePropertyAccessor());

        // Enables Membrane functions in SpEL scripts like 'hasScopes("admin")'
        addMethodResolver(new BuiltInFunctionResolver(router, localXmlConfig));

        addTypeConverters();
    }

    private void addTypeConverters() {
        var cs = new DefaultConversionService();
        cs.addConverter(new SpELMapToStringTypeConverter());
        cs.addConverter(new SpELBodyToStringTypeConverter());
        cs.addConverter(new ListToStringTypeConverter());
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
            params = new SpELMap<>(URLParamUtil.getParams(new URIFactory(), exchange));
        } catch (Exception e) {
            // Details are logged in URLParamUtil.getParams
            log.info("Error parsing query parameters for request '{} {}'", method, exchange.getOriginalRequestUri());
            log.debug("Path: {}", path,e);
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

    public Object getIt() {
        return exchange.getProperty("it");
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

    public SpELLablePropertyAware getCookies() {
        return cookies;
    }

    /**
     * Also get cookies with cookies.fieldname
     */
    public SpELLablePropertyAware getCookie() {
        return cookies;
    }

    /**
     * Get gateway information with e.g. gateway.version
     */
    public SpELLablePropertyAware getGateway() {
        return gateway;
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

    public LazyBody getBody() { return body; }

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
