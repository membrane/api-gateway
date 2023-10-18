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
import com.predic8.membrane.core.lang.spel.spelable.*;
import org.springframework.expression.spel.support.*;

import java.io.*;
import java.util.*;

public class ExchangeEvaluationContext extends StandardEvaluationContext {

    private static  final ObjectMapper om = new ObjectMapper();

    private final Exchange exchange;
    private final Message message;
    private final SPeLablePropertyAware headers;
    private final SPeLablePropertyAware properties;
    private final String path;
    private final String method;

    private final SPelMessageWrapper request;
    private final SPelMessageWrapper response;

    public ExchangeEvaluationContext(Exchange exc) {
        this(exc, exc.getRequest());
    }

    public ExchangeEvaluationContext(Exchange exc, Message message) {
        super();

        this.exchange = exc;
        this.message = message;
        this.properties = new SPeLProperties(exc.getProperties());
        this.headers = new SpeLHeader(message.getHeader());

        Request request = exc.getRequest();
        path = request.getUri();
        method = request.getMethod();

        this.request = new SPelMessageWrapper(exc.getRequest());
        this.response = new SPelMessageWrapper(exc.getResponse());

        setRootObject(this);
        addPropertyAccessor(new AwareExchangePropertyAccessor());
    }


    public SPeLablePropertyAware getProperties() {
        return properties;
    }

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

    public SPelMessageWrapper getRequest() {
        return request;
    }

    public SPelMessageWrapper getResponse() {
        return response;
    }

    public SPeLMap<String, Object> getJson() throws IOException {
        return new SPeLMap<String, Object>(om.readValue(message.getBodyAsStreamDecoded(), Map.class));
    }
}
