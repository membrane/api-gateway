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

package com.predic8.membrane.core.interceptor.json;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.google.common.io.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static com.fasterxml.jackson.core.JsonTokenId.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.createProblemDetails;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.EnumSet.*;

/**
 * Enforces JSON restrictions in requests
 */
@MCElement(name = "jsonProtection")
public class JsonProtectionInterceptor extends AbstractInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JsonProtectionInterceptor.class);

    private final ObjectMapper om = new ObjectMapper()
            .configure(FAIL_ON_READING_DUP_TREE_KEY, true)
            .configure(STRICT_DUPLICATE_DETECTION, true);

    private Boolean reportError;
    private int maxTokens = 10000;
    private int maxSize = 50 * 1024 * 1024;
    private int maxDepth = 50;
    private int maxStringLength = 262144;
    private int maxKeyLength = 256;
    private int maxObjectSize = 1000;
    private int maxArraySize = 1000;


    public JsonProtectionInterceptor() {
        name = "JSON protection";
        setFlow(of(REQUEST));
    }

    @Override
    public void init() throws Exception {
        if (maxStringLength < maxKeyLength)
            maxKeyLength = maxStringLength;
    }

    private boolean shouldProvideDetails() {
        if (reportError != null) {
            return reportError;
        }
        return !router.isProduction();
    }

    private abstract static class Context {
        public abstract void check(JsonToken jsonToken, JsonParser parser) throws IOException, JsonProtectionException;
    }

    private class ObjContext extends Context {
        int n;
        @Override
        public void check(JsonToken jsonToken, JsonParser parser) throws JsonProtectionException, IOException {
            if (jsonToken.id() == ID_END_OBJECT)
                return;
            n++;
            if (n > maxObjectSize)
                throw new JsonProtectionException("Exceeded maxObjectSize.");
            if (parser.getCurrentName().length() > maxKeyLength) {
                throw new JsonProtectionException("Exceeded maxKeyLength.");
            }
        }
    }

    private class ArrContext extends Context {
        int n;

        @Override
        public void check(JsonToken jsonToken, JsonParser parser) throws JsonProtectionException {
            if (jsonToken.id() == ID_END_ARRAY)
                return;
            n++;
            if (n > maxArraySize)
                throw new JsonProtectionException("Exceeded maxArraySize.");
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        if ("GET".equals(exc.getRequest().getMethod()))
            return CONTINUE;
        try {
            parseJson(new CountingInputStream(exc.getRequest().getBodyAsStreamDecoded()));
        } catch (JsonProtectionException e) {
            exc.setResponse(createErrorResponse(e));
            return RETURN;
        } catch (Throwable e) {
            LOG.debug(e.getMessage());
            exc.setResponse(createErrorResponse(e));
            return RETURN;
        }
        return CONTINUE;
    }

    private Response createErrorResponse(Throwable e) {
        if (shouldProvideDetails()) {
            Map<String, Object> details = new HashMap<>() {{
                put("message", e.getMessage());
            }};
            return createProblemDetails(400, "/security/json-validation", "JSON Protection Violation", details);
        }
        return Response.badRequest().build();
    }

    private void parseJson(CountingInputStream cis) throws IOException, JsonProtectionException {
        JsonParser parser = om.createParser(cis);
        int tokenCount = 0;
        int depth = 0;
        List<Context> contexts = new ArrayList<>();
        Context currentContext = null;
        while (true) {
            JsonToken jsonToken = parser.nextValue();
            if (jsonToken == null)
                break;
            tokenCount++;
            if (tokenCount > maxTokens)
                throw new JsonProtectionException("Exceeded maxTokens.");
            if (cis.getCount() > maxSize)
                throw new JsonProtectionException("Exceeded maxSize.");
            if (currentContext != null)
                currentContext.check(jsonToken, parser);
            switch (jsonToken.id()) {
                case ID_START_OBJECT:
                    depth++;
                    if (depth > maxDepth)
                        throw new JsonProtectionException("Exceeded maxDepth.");
                    contexts.add(currentContext = new ObjContext());
                    break;
                case ID_START_ARRAY:
                    depth++;
                    if (depth > maxArraySize)
                        throw new JsonProtectionException("Exceeded maxArraySize.");
                    contexts.add(currentContext = new ArrContext());
                    break;
                case ID_END_OBJECT:
                case ID_END_ARRAY:
                    depth--;
                    if (depth < 0)
                        throw new JsonProtectionException("Invalid JSON Document.");
                    contexts.remove(contexts.size() - 1);
                    currentContext = contexts.size() == 0 ? null : contexts.get(contexts.size() - 1);
                    break;
                case ID_STRING:
                    if (parser.getValueAsString().length() > maxStringLength)
                        throw new JsonProtectionException("Exceeded maxStringLength.");
                    break;
                case ID_NUMBER_INT:
                case ID_NUMBER_FLOAT:
                case ID_TRUE:
                case ID_FALSE:
                case ID_NULL:
                    break;
                case ID_NOT_AVAILABLE:
                case ID_NO_TOKEN:
                case ID_FIELD_NAME:
                case ID_EMBEDDED_OBJECT:
                    throw new JsonProtectionException("Not handled.");
                default:
                    throw new JsonProtectionException("Not handled (\" + jsonToken.id() + \")");
            }
        }
        if (cis.getCount() > maxSize)
            throw new JsonProtectionException("Exceeded maxSize.");
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * @description Overwrites default error reporting behaviour. If set to true, errors will provide ProblemDetails body,
     * if set to false, errors will throw standard exceptions.
     * @default null
     * @param reportError
     */
    @MCAttribute
    public void setReportError(boolean reportError) {
        this.reportError = reportError;
    }

    public boolean getReportError() {
        return reportError;
    }

    /**
     * @description Maximum number of tokens a JSON document may consist of. For example, <code>{"a":"b"}</code> counts
     * as 3.
     * @default 10000
     * @param maxTokens
     */
    @MCAttribute
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * @description Maximum total size of the JSON document in bytes.
     * @default 52428800
     * @param maxSize
     */
    @MCAttribute
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * @description Maximum depth of nested JSON structures. For example, <code>{"a":{"b":{"c":"d"}}}</code> has a depth
     * of 3.
     * @default 50
     * @param maxDepth
     */
    @MCAttribute
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * @description Maximum string length. For example, <code>{"abcd": "efgh", "ijkl": [ "mnop" ], "qrst": { "uvwx":
     * 1}}</code> has a maximum string length of 4. (In this example, all 6 strings effectively have length 4.)
     * <p>
     * The maximum string length also affects keys ("abcd", "ijkl", "qrst" and "uvwx" in the example). The keys can be
     * also limited by the separate property maxKeyLength. The stricter limit applies.
     * @default 262144
     * @param maxStringLength
     */
    @MCAttribute
    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    public int getMaxKeyLength() {
        return maxKeyLength;
    }

    /**
     * @description Maximum key length. For example, <code>{"abcd": "efgh123", "ijkl": [ "mnop123" ], "qrst": { "uvwx":
     * 1}}</code> has a maximum key length of 4. (In this example, all 4 strings used as keys effectively have length
     * 4.)
     * <p>
     * The maximum key length also affects strings ("abcd", "ijkl", "qrst" and "uvwx" in the example). The strings can be
     * also limited by the separate property maxStringLength. The stricter limit applies.
     * @default 256
     * @param maxKeyLength
     */
    @MCAttribute
    public void setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    public int getMaxObjectSize() {
        return maxObjectSize;
    }

    /**
     * @description Maximum size of JSON objects. For example, <code>{"a": {"b":"c", "d": "e"}, "f": "g"}</code> has a
     * maximum object size of 2. (In this example, both objects effectively have a size of 2.)
     * @default 1000
     * @param maxObjectSize
     */
    @MCAttribute
    public void setMaxObjectSize(int maxObjectSize) {
        this.maxObjectSize = maxObjectSize;
    }

    public int getMaxArraySize() {
        return maxArraySize;
    }

    /**
     * @description Maximum size of JSON objects. For example, <code>{"a": {"b":"c", "d": "e"}, "f": "g"}</code> has a
     * maximum object size of 2. (In this example, both objects effectively have a size of 2.)
     * @default 1000
     * @param maxArraySize
     */
    @MCAttribute
    public void setMaxArraySize(int maxArraySize) {
        this.maxArraySize = maxArraySize;
    }

    @Override
    public String getShortDescription() {
        return "Protects against several JSON attack classes.";
    }

    @Override
    public String getLongDescription() {
        return "<div>Enforces the following constraints:<br/><ul>" +
                "<li>HTTP request body must be well-formed JSON, if the HTTP verb is not" +
                "<font style=\"font-family: monospace\">GET</font>.</li>" +
                "<li>Limits the maximum number of tokens to " + maxTokens + ". (Each string and opening bracket counts" +
                "as a token: <font style=\"font-family: monospace\">{\"a\":\"b\"}</font> counts as 3 tokens)</li>" +
                "<li>Forbids duplicate keys. (<font style=\"font-family: monospace\">{\"a\":\"b\", \"a\":\"c\"}</font> " +
                "will be rejected.)</li>" +
                "<li>Limits the total size in bytes of the body to " + maxSize + ".</li>" +
                "<li>Limits the maximum depth to " + maxDepth + ". (<font style=\"font-family: monospace\">{\"a\":[{\"b\"" +
                ":\"c\"}]}</font> has depth 3.)</li>" +
                "<li>Limits the maximum string length to " + maxStringLength + ". " +
                "(<font style=\"font-family: monospace\">{\"a\":\"abc\"}</font> has max string length 3.)</li>" +
                "<li>Limits the maximum key length to " + maxKeyLength + ". " +
                "(<font style=\"font-family: monospace\">{\"abc\":\"a\"}</font> has key length 3.)</li>" +
                "<li>Limits the maximum object size to " + maxObjectSize + ". " +
                "(<font style=\"font-family: monospace\">{\"a\":\"b\",\"c\":\"d\"}</font> has object size 2.)</li>" +
                "<li>Limits the maximum array size to " + maxArraySize + ". " +
                "(<font style=\"font-family: monospace\">[\"a\", \"b\"]</font> has array size 2.)</li>" +
                "</ul></div>";
    }
}
