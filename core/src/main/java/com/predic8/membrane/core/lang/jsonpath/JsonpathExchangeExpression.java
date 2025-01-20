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
import com.fasterxml.jackson.databind.exc.*;
import com.jayway.jsonpath.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.Interceptor.*;
import com.predic8.membrane.core.lang.*;
import org.jose4j.json.internal.json_simple.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.StringUtil.*;
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
            if (type.getName().equals("java.lang.Object") || type.isInstance(o)) {
                return type.cast(o);
            }
            if (type.isAssignableFrom(Boolean.class)) {
                if (o instanceof Boolean b) {
                    return type.cast(b);
                }
                return type.cast(convertToBoolean(o));
            }
            if (type.isAssignableFrom(String.class)) {
                if (o instanceof List l) {
                    return type.cast(l.getFirst().toString());
                }
                if (o instanceof JSONAware ja) {
                    return type.cast(ja.toJSONString());
                }
                return type.cast(o.toString());
            }
            if (o instanceof Integer i) {
                return type.cast(String.valueOf(i));
            }
            // Map and List are covered by the next line
            return type.cast(o);
        } catch (PathNotFoundException e) {
            if (type.isAssignableFrom(Boolean.class)) {
                return type.cast( FALSE);
            }
            return null;
        } catch (InvalidPathException ipe) {
            throw new ExchangeExpressionException(expression, ipe)
                    .message(ipe.getLocalizedMessage());
        }
        catch (MismatchedInputException e) {
            String body = exchange.getMessage(flow).getBodyAsStringDecoded();
            if (body == null || body.isEmpty()) {
                log.info("Error evaluating Jsonpath {}. Body is empty!", expression);
            } else {
                log.info("Error evaluating Jsonpath {}. Body is: {}", expression, truncateAfter(body,200));
            }
            throw new ExchangeExpressionException(expression, e);
        }
        catch (Exception e) {
            log.info("Error evaluating Jsonpath {}. Got message {}", expression , e);
            throw new ExchangeExpressionException(expression, e);
        }
    }

    private boolean convertToBoolean(Object o) {
        return o != null;
    }

    private Object execute(Exchange exchange, Flow flow) throws IOException {
        return JsonPath.read(om.readValue(exchange.getMessage(flow).getBodyAsStream(), Map.class), expression);
    }
}
