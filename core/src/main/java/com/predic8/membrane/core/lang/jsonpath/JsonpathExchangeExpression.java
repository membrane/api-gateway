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
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.jose4j.json.internal.json_simple.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.util.StringUtil.*;
import static java.lang.Boolean.*;
import static java.nio.charset.StandardCharsets.*;

public class JsonpathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(JsonpathExchangeExpression.class);

    private final ObjectMapper om = new ObjectMapper();

    public JsonpathExchangeExpression(String source) {
        super(source);
        syntaxCheckJsonpath(source);
    }

    private static void syntaxCheckJsonpath(String source) {
        try {
            JsonPath.read(new ByteArrayInputStream("{}".getBytes(UTF_8)), source);
        } catch (PathNotFoundException ignore) {
            // It is normal that nothing is found in an empty document
        } catch (Exception e) {
            throw new ConfigurationException("""
                    The jsonpath expression:
                    
                    %s
                    
                    cannot be compiled.
                    
                    Error: %s""".formatted(source, e));
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Flow flow, Class<T> type) {

        // Guard against empty body and other Content-Types
        try {
            if (exchange.getMessage(flow).isBodyEmpty() || !exchange.getMessage(flow).isJSON()) {
                log.debug("Body is empty or Content-Type not JSON. Nothing to evaluate for expression: {}", expression); // Normal
                return resultForNoEvaluation(type);
            }
        } catch (IOException e) {
            log.error("Error checking if body is empty", e);
            return resultForNoEvaluation(type);
        }

        try {
            return castType(exchange, flow, type);
        } catch (PathNotFoundException e) {
            if (type.isAssignableFrom(Boolean.class)) {
                return type.cast(FALSE);
            }
            return null;
        } catch (InvalidPathException ipe) {
            log.error("{} is an invalid jsonpath: {}", expression, ipe.getMessage());
            throw new ExchangeExpressionException(expression, ipe);
        } catch (MismatchedInputException e) {
            String body = exchange.getMessage(flow).getBodyAsStringDecoded();
            if (body == null || body.isEmpty()) {
                log.info("Error evaluating Jsonpath {}. Body is empty!", expression);
            } else {
                log.info("Error evaluating Jsonpath {}. Body is: {}", expression, truncateAfter(body, 200));
            }
            throw new ExchangeExpressionException(expression, e);
        } catch (Exception e) {
            log.info("Error evaluating Jsonpath {}. Got message {}", expression, e);
            throw new ExchangeExpressionException(expression, e);
        }
    }

    private <T> @Nullable T castType(Exchange exchange, Flow flow, Class<T> type) throws IOException {
        Object o = execute(exchange, flow);
        if (type.getName().equals("java.lang.Object") || type.isInstance(o)) {
            return type.cast(o);
        }
        if (Boolean.class.isAssignableFrom(type)) {
            if (o instanceof Boolean b) {
                return type.cast(b);
            }
            return type.cast(convertToBoolean(o));
        }
        if (String.class.isAssignableFrom(type)) {
            if (o instanceof List l) {
                // Render list as String like: [ 1, 2, 3]
                // That is different from XPath where you get the value of the first node as String. But
                // it is consistent with most JSONPath implementations.
                return type.cast(l.toString());
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
    }

    private <T> T resultForNoEvaluation(Class<T> type) {
        if (String.class.isAssignableFrom(type)) {
            return type.cast("");
        }
        if (Boolean.class.isAssignableFrom(type)) {
            return type.cast(FALSE);
        }
        return type.cast(new Object());
    }

    private boolean convertToBoolean(Object o) {
        return o != null;
    }

    private Object execute(Exchange exchange, Flow flow) throws IOException {
        return JsonPath.read(om.readValue(exchange.getMessage(flow).getBodyAsStreamDecoded(), Object.class), expression);
    }
}
