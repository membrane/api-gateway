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
import org.jose4j.jwt.JwtClaims;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.*;

import java.io.*;
import java.util.*;

public class ExchangeEvaluationContext  {

    private static  final ObjectMapper om = new ObjectMapper();

    private final Exchange exchange;
    private final Message message;
    private final SPeLablePropertyAware headers;
//    private final SPeLablePropertyAware properties;
    private final Map<String, Object> properties;
    private final String path;
    private final String method;

    public ExchangeEvaluationContext(Exchange exchange, Message message) {
        this.exchange = exchange;
        this.message = message;
        properties = exchange.getProperties();
//        ;
//        this.properties = new PropertiesMap(exchange.getProperties());
//        this.headers = new HeaderMap(message.getHeader());
        this.headers = message.getHeader();

        Request request = exchange.getRequest();
        path = request.getUri();
        method = request.getMethod();
    }

    private Map.Entry<String, Object> prettifyEntriesForSpelUsage(Map.Entry<String, Object> entry) {
        if (entry.getKey().equals("jwt") && entry.getValue() instanceof JwtClaims) {
            return new AbstractMap.SimpleEntry<>("jwt", ((JwtClaims) entry.getValue()).getClaimsMap());
        }
        return entry;
    }

    public StandardEvaluationContext getStandardEvaluationContext() {
        var ctx = new StandardEvaluationContext(this);
        ctx.addPropertyAccessor(new AwareExchangePropertyAccessor());
        return ctx;
    }

//    public Map<String, Object> getProperties() {
//        return properties;
//    }

    // We need to expose HeaderMap outside its defined visibility scope for SpEL
    public SPeLablePropertyAware getHeaders() {
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

    @SuppressWarnings("rawtypes")
    public Map getJson() throws IOException {
        return om.readValue(message.getBodyAsStreamDecoded(), Map.class);
    }

//    @Override
//    public boolean canRead(EvaluationContext context, Object target, String name) {
//        return true;
//    }
//
//    @Override
//    public TypedValue read(EvaluationContext context, Object target, String name) {
//        try {
//            var field = target.getClass().getDeclaredField(name);
//            field.setAccessible(true);
//
//            return new TypedValue(field.get(target));
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
