/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.lang.jsonpath;

import com.fasterxml.jackson.databind.*;
import com.jayway.jsonpath.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

public class JsonpathExchangeExpression implements ExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(JsonpathExchangeExpression.class);

    private final ObjectMapper om = new ObjectMapper();

    private final String source;

    public JsonpathExchangeExpression(String source) {
        this.source = source;
    }

    @Override
    public boolean evaluate(Exchange exchange, Interceptor.Flow flow) {

        try {
            return execute(exchange, flow) != null;
        } catch (PathNotFoundException e) {
            return false;
        } catch (Exception e) {
            log.error("Error evaluating Jsonpath expression {}. Got message {}", source, e);
            throw new RuntimeException("Error evaluating Jsonpath expression");
        }
    }

    private Object execute(Exchange exchange, Interceptor.Flow flow) throws IOException {
        return JsonPath.read(om.readValue(exchange.getMessage(flow).getBodyAsStream(), Map.class), source);
    }
}
