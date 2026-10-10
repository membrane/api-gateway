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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.interceptor.Interceptor.Flow;
import com.predic8.membrane.core.lang.AbstractExchangeExpression;
import com.predic8.membrane.core.lang.BodyKind;
import com.predic8.membrane.core.lang.ExchangeExpressionException;
import com.predic8.membrane.core.router.Router;
import com.predic8.membrane.core.util.ConfigurationException;
import org.jetbrains.annotations.Nullable;
import org.jose4j.json.internal.json_simple.JSONAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_X_WWW_FORM_URLENCODED;
import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyMap;
import static java.nio.charset.StandardCharsets.UTF_8;

public class JsonpathExchangeExpression extends AbstractExchangeExpression {

    private static final Logger log = LoggerFactory.getLogger(JsonpathExchangeExpression.class);

    /**
     * What an empty body, or one that holds no JSON, is evaluated against: a document without content.
     */
    private static final Object EMPTY_DOCUMENT = emptyMap();

    private final ObjectMapper om = new ObjectMapper();

    /**
     * Compiled once: the static {@code JsonPath.read(json, String)} looks the path up in Jayway's
     * JVM-wide LRU cache, whose every access takes a single lock, serializing all request threads.
     * A compiled {@link JsonPath} is immutable and can be shared across threads.
     */
    private final JsonPath compiledPath;

    public JsonpathExchangeExpression(String source, Router router) {
        super(source, router);
        syntaxCheckJsonpath(source);
        compiledPath = JsonPath.compile(source);
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
        var document = document(exchange.getMessage(flow));
        try {
            return castType(document, type);
        } catch (PathNotFoundException e) {
            if (type.isAssignableFrom(Boolean.class)) {
                return type.cast(FALSE);
            }
            return null;
        } catch (InvalidPathException e) {
            log.error("Invalid JSONPath: {}", expression);
            throw new ExchangeExpressionException(expression, e, "Invalid JSONPath.")
                    .excludeException();
        } catch (Exception e) {
            log.info("Error evaluating JSONPath: {}", expression);
            throw new ExchangeExpressionException(expression, e, "Error evaluating JSONPath")
                    .body(exchange.getMessage(flow).getBodyAsStringDecoded())
                    .excludeException();
        }
    }

    private Object document(Message msg) {
        return switch (BodyKind.of(msg, MimeType::isJson)) {
            case EMPTY, FOREIGN -> EMPTY_DOCUMENT;
            case MATCHING, UNKNOWN -> parse(msg);
            case FORM -> parseFormBody(msg);
        };
    }

    private Object parse(Message msg) {
        try {
            return readBody(msg);
        } catch (MismatchedInputException e) {
            log.info("Body is not valid JSON. JSONPath: {} Token: {} Target: {}", expression, e.getCurrentToken(), e.getTargetType());
            throw new ExchangeExpressionException(expression, e, "Body is not valid JSON: " + e.getOriginalMessage())
                    .body(msg.getBodyAsStringDecoded())
                    .extension("token", e.getCurrentToken())
                    .extension("targetType", e.getTargetType())
                    .excludeException()
                    .bodyError();
        } catch (JsonProcessingException e) {
            log.info("Body is not valid JSON. JSONPath: {}", expression);
            throw new ExchangeExpressionException(expression, e, "Body is not valid JSON: " + e.getOriginalMessage())
                    .body(msg.getBodyAsStringDecoded())
                    .excludeException()
                    .bodyError();
        } catch (IOException e) {
            throw new ExchangeExpressionException(expression, e, "Error reading body for JSONPath")
                    .excludeException();
        }
    }

    /**
     * Form data is what <code>curl -d</code> sends for any body, so it may be mislabelled JSON. Anything
     * else is the form data it claims to be, which holds no JSON document.
     */
    private Object parseFormBody(Message msg) {
        try {
            return readBody(msg);
        } catch (JsonProcessingException e) {
            log.info("Content-Type is {} and the body is not JSON, so JSONPath {} is evaluated against an empty document. To have the body evaluated, send Content-Type: {}",
                    APPLICATION_X_WWW_FORM_URLENCODED, expression, APPLICATION_JSON);
            return EMPTY_DOCUMENT;
        } catch (IOException e) {
            throw new ExchangeExpressionException(expression, e, "Error reading body for JSONPath")
                    .excludeException();
        }
    }

    private Object readBody(Message msg) throws IOException {
        return om.readValue(msg.getBodyAsStreamDecoded(), Object.class);
    }

    private <T> @Nullable T castType(Object document, Class<T> type) {
        Object o = compiledPath.read(document);
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

    /**
     * An indefinite path (filter, wildcard, deep scan) returns the list of its matches, so an
     * empty list means nothing matched. A definite path returns the value itself, so an existing
     * but empty array is still true.
     */
    private boolean convertToBoolean(Object o) {
        if (o instanceof List<?> l && !compiledPath.isDefinite()) {
            return !l.isEmpty();
        }
        return o != null;
    }
}
