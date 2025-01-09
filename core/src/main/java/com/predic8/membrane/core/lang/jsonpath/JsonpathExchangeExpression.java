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
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.interceptor.lang.*;
import com.predic8.membrane.core.lang.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static java.lang.Boolean.*;

public class JsonpathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(JsonpathExchangeExpression.class);

    private final ObjectMapper om = new ObjectMapper();

    public JsonpathExchangeExpression(String source) {
        super(source);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Flow flow, Class<T> type) {
        try {
            Object o = execute(exchange, flow);
            if (type.isAssignableFrom(Boolean.class)) {
                if (o instanceof Boolean b) {
                    return (T) b;
                }
                return convertToBoolean(o);
            }
            if (o instanceof Integer i) {
                if (type.isAssignableFrom(Integer.class)) {
                    return (T) String.valueOf(i);
                }
                return (T) String.valueOf(o);
            }
            return type.cast(o);
        } catch (PathNotFoundException e) {
            if (type.isAssignableFrom(Boolean.class)) {
                return (T) FALSE;
            }
            return null;
        } catch (InvalidPathException ipe) {
            throw new ExchangeExpressionException(expression, ipe)
                    .message(ipe.getLocalizedMessage());
        }
        catch (Exception e) {
            log.error("Error evaluating Jsonpath expression {}. Got message {}", expression , e);
            throw new ExchangeExpressionException(expression, e);
        }
    }

    private <T> @NotNull T convertToBoolean(Object o) {
        if (o != null)
            return (T) TRUE;
        else
            return (T) FALSE;
    }

    private Object execute(Exchange exchange, Flow flow) throws IOException {
        return JsonPath.read(om.readValue(exchange.getMessage(flow).getBodyAsStream(), Map.class), expression);
    }
}
