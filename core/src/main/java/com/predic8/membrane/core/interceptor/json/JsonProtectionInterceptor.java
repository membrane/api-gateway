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
import com.predic8.membrane.core.exceptions.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.multipart.*;
import com.predic8.membrane.core.multipart.MultipartUtil.PartAction;
import org.slf4j.*;

import java.io.*;
import java.util.*;

import static com.fasterxml.jackson.core.JsonParser.Feature.*;
import static com.fasterxml.jackson.core.JsonTokenId.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.isJson;
import static com.predic8.membrane.core.interceptor.json.JsonProtectionInterceptor.OtherContentTypes.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.util.EnumSet.*;

/**
 * @description <p>Enforces restrictions on JSON request bodies to protect against JSON-based attacks and resource exhaustion.
 * Validates against configurable limits to prevent attacks such as:</p>
 * <ul>
 *   <li>Deeply nested JSON structures (billion laughs attack)</li>
 *   <li>Memory exhaustion from oversized payloads</li>
 *   <li>Prototype pollution via __proto__ keys in JavaScript backends</li>
 *   <li>Duplicate key attacks</li>
 * </ul>
 * <p>JSON documents carried inside a multipart body are inspected part by part, so a JSON document
 * uploaded as an attachment is checked like a plain JSON body.</p>
 *
 * @yaml
 * <pre><code>
 * - jsonProtection:
 *     maxDepth: 20
 *     maxKeyLength: 50
 *     maxObjectSize: 100
 *     maxTokens: 1000
 *     maxStringLength: 1000
 *     maxArraySize: 100
 *     maxSize: 10000
 *     blockProto: true
 *     reportError: true
 *     otherContentTypes: SKIP
 * </code></pre>
 *
 * @topic 3. Security and Validation
 */
@SuppressWarnings("unused")
@MCElement(name = "jsonProtection")
public class JsonProtectionInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonProtectionInterceptor.class);

    /**
     * What to do with content that is not JSON: the whole body of a non-JSON request, or a
     * non-JSON part of a multipart body.
     */
    public enum OtherContentTypes {
        REJECT, SKIP
    }

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
    private boolean blockProto = true;
    private OtherContentTypes otherContentTypes = REJECT;

    public JsonProtectionInterceptor() {
        name = "json protection";
        setAppliedFlow(of(REQUEST));
    }

    @Override
    public void init() {
        super.init();
        if (maxStringLength < maxKeyLength)
            maxKeyLength = maxStringLength;
    }

    private boolean shouldProvideDetails() {
        if (reportError != null) {
            return reportError;
        }
        return !router.getConfiguration().isProduction();
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
                throw new JsonProtectionException("Exceeded maxObjectSize.",
                                                    parser.currentLocation().getLineNr(),
                                                    parser.currentLocation().getColumnNr());
            if (blockProto && "__proto__".equals(parser.currentName()))
                throw new JsonProtectionException("__proto__ found as key.",
                        parser.currentLocation().getLineNr(),
                        parser.currentLocation().getColumnNr());
            if (parser.currentName().length() > maxKeyLength) {
                throw new JsonProtectionException("Exceeded maxKeyLength.",
                                                    parser.currentLocation().getLineNr(),
                                                    parser.currentLocation().getColumnNr());
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
                throw new JsonProtectionException("Exceeded maxArraySize.",
                                                    parser.currentLocation().getLineNr(),
                                                    parser.currentLocation().getColumnNr());
        }
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if ("GET".equals(exc.getRequest().getMethod()))
            return CONTINUE;

        Request request = exc.getRequest();
        try {
            // The common case: a plain body is inspected straight off the body stream, without
            // buffering it into a byte[] first.
            if (!MultipartUtil.isMultipart(request))
                return inspect(exc, request.getHeader().getContentType(), request.getBodyAsStreamDecoded(), null);

            return inspectParts(exc, request);
        } catch (Exception e) {
            log.debug(e.getMessage());
            exc.setResponse(createErrorResponse(e.getMessage(), null, null));
            return RETURN;
        }
    }

    /**
     * Inspects the JSON parts of a multipart body. Parts that are not JSON are decided from their
     * header and never buffered, and no part may exceed {@link #maxSize} - so a large upload does not
     * have to be held in memory to be rejected.
     */
    private Outcome inspectParts(Exchange exc, Request request) throws Exception {
        var outcome = new Outcome[]{CONTINUE};
        MultipartUtil.forEachPart(request, maxSize, new MultipartUtil.PartHandler() {
            @Override
            public PartAction decide(Header partHeader) {
                if (outcome[0] != CONTINUE)
                    return PartAction.STOP;
                if (isJson(partHeader.getContentType()))
                    return PartAction.INSPECT;
                if (otherContentTypes == SKIP)
                    return PartAction.SKIP;
                // Rejecting needs the header only, so the offending body is never buffered.
                outcome[0] = rejectNonJson(exc, partHeader.getContentType(), partName(partHeader));
                return PartAction.STOP;
            }

            @Override
            public void handle(Part part) {
                outcome[0] = inspect(exc, part.getContentType(), part.getInputStream(), partName(part.getHeader()));
            }
        });
        return outcome[0];
    }

    /**
     * Inspects a single content unit: either the whole body or one MIME part.
     *
     * @param partName the name of the MIME part, or null if the unit is the whole body
     */
    private Outcome inspect(Exchange exc, String contentType, InputStream body, String partName) {
        if (!isJsonUnit(contentType, partName)) {
            if (otherContentTypes == SKIP)
                return CONTINUE;
            return rejectNonJson(exc, contentType, partName);
        }
        try {
            parseJson(new CountingInputStream(body));
        } catch (JsonProtectionException e) {
            log.debug(e.getMessage());
            exc.setResponse(createErrorResponse(describe(e.getMessage(), partName), e.getLine(), e.getCol()));
            return RETURN;
        } catch (JsonParseException e) {
            log.debug(e.getMessage());
            exc.setResponse(createErrorResponse(describe(e.getMessage(), partName),
                    e.getLocation().getLineNr(), e.getLocation().getColumnNr()));
            return RETURN;
        } catch (Throwable e) {
            log.debug(e.getMessage());
            exc.setResponse(createErrorResponse(describe(e.getMessage(), partName), null, null));
            return RETURN;
        }
        return CONTINUE;
    }

    /**
     * A whole body without a Content-Type is still parsed, as it was before multipart support: clients
     * that post JSON without declaring it must keep being checked. A MIME part without a Content-Type
     * defaults to text/plain per RFC 2045 and is therefore not JSON.
     */
    private static boolean isJsonUnit(String contentType, String partName) {
        if (contentType == null)
            return partName == null;
        return isJson(contentType);
    }

    private Outcome rejectNonJson(Exchange exc, String contentType, String partName) {
        String msg = "Content-Type %s is not JSON. Set otherContentTypes to \"skip\" to pass non-JSON content through."
                .formatted(contentType);
        log.debug(msg);
        exc.setResponse(createErrorResponse(describe(msg, partName), null, null));
        return RETURN;
    }

    private static String partName(Header partHeader) {
        String name = Part.nameOf(partHeader) != null ? Part.nameOf(partHeader) : Part.contentIDOf(partHeader);
        return name != null ? name : "";
    }

    /**
     * Names the offending part, so a rejection on a multipart upload can be traced back to one attachment.
     */
    private static String describe(String message, String partName) {
        if (partName == null)
            return message;
        return partName.isEmpty() ? "In one part of the multipart body: " + message
                                  : "In part '%s': %s".formatted(partName, message);
    }

    private Response createErrorResponse(String msg, Integer line, Integer col) {
        if (shouldProvideDetails()) {
            log.warn("JSON protection violation. Line: {}, col: {}, msg: {}", line, col, msg);
            ProblemDetails pd = user(false,getDisplayName())
                    .status(400)
                    .title("JSON Protection Violation")
                    .detail(msg);
            if (line != null) pd.topLevel("line", line);
            if (col != null) pd.topLevel("column", col);
            return pd.build();
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
                throw new JsonProtectionException("Exceeded maxTokens.",
                                                    parser.currentLocation().getLineNr(),
                                                    parser.currentLocation().getColumnNr());
            if (cis.getCount() > maxSize)
                throw new JsonProtectionException("Exceeded maxSize.",
                                                    parser.currentLocation().getLineNr(),
                                                    parser.currentLocation().getColumnNr());
            if (currentContext != null)
                currentContext.check(jsonToken, parser);
            switch (jsonToken.id()) {
                case ID_START_OBJECT:
                    depth++;
                    if (depth > maxDepth)
                        throw new JsonProtectionException("Exceeded maxDepth.",
                                                            parser.currentLocation().getLineNr(),
                                                            parser.currentLocation().getColumnNr());
                    contexts.add(currentContext = new ObjContext());
                    break;
                case ID_START_ARRAY:
                    depth++;
                    if (depth > maxDepth)
                        throw new JsonProtectionException("Exceeded maxDepth.",
                                                            parser.currentLocation().getLineNr(),
                                                            parser.currentLocation().getColumnNr());
                    contexts.add(currentContext = new ArrContext());
                    break;
                case ID_END_OBJECT:
                case ID_END_ARRAY:
                    depth--;
                    if (depth < 0)
                        throw new JsonProtectionException("Invalid JSON Document.",
                                                            parser.currentLocation().getLineNr(),
                                                            parser.currentLocation().getColumnNr());
                    contexts.removeLast();
                    currentContext = contexts.isEmpty() ? null : contexts.getLast();
                    break;
                case ID_STRING:
                    if (parser.getValueAsString().length() > maxStringLength)
                        throw new JsonProtectionException("Exceeded maxStringLength.",
                                                            parser.currentLocation().getLineNr(),
                                                            parser.currentLocation().getColumnNr());
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
                    throw new JsonProtectionException("Not handled.",
                                                        parser.currentLocation().getLineNr(),
                                                        parser.currentLocation().getColumnNr());
                default:
                    throw new JsonProtectionException("Not handled (\" + jsonToken.id() + \")",
                                                        parser.currentLocation().getLineNr(),
                                                        parser.currentLocation().getColumnNr());
            }
        }
        if (cis.getCount() > maxSize)
            throw new JsonProtectionException("Exceeded maxSize.",
                                                parser.currentLocation().getLineNr(),
                                                parser.currentLocation().getColumnNr());
    }

    @SuppressWarnings("unused")
    public int getMaxTokens() {
        return maxTokens;
    }

    /**
     * @description Overwrites default error reporting behaviour. If set to true, errors will provide ProblemDetails body,
     * if set to false, errors will throw exceptions resulting in 400 Bad Request responses without any details.
     * @default Depends on production configuration. In production mode default is false otherwise true.
     * @param reportError
     */
    @MCAttribute
    public void setReportError(boolean reportError) {
        this.reportError = reportError;
    }

    @SuppressWarnings("unused")
    public Boolean getReportError() {
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
     * @description Maximum total size of the JSON document in bytes. The limit is per document, so
     * in a multipart body it applies to each JSON part separately rather than to the whole upload.
     * To cap the size of the entire request, use the <code>limit</code> plugin with its
     * <code>maxBodyLength</code> attribute.
     * @default 52428800
     * @param maxSize
     */
    @MCAttribute
    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public int getMaxStringLength() {
        return maxStringLength;
    }

    /**
     * @description Maximum string length. For example, <code>{"abcd": "efgh", "ijkl": [ "mnop" ], "qrst": { "uvwx":
     * 1}}</code> has a maximum string length of 4. (In this example, all 6 strings effectively have length 4.)
     * <p>
     * The maximum string length also affects keys ("abcd", "ijkl", "qrst" and "uvwx" in the example). The keys can be
     * also limited by the separate property maxKeyLength. The stricter limit applies.
     * </p>
     * @default 262144
     * @param maxStringLength
     */
    @MCAttribute
    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }

    @SuppressWarnings("unused")
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
     * </p>
     * @default 256
     * @param maxKeyLength
     */
    @MCAttribute
    public void setMaxKeyLength(int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
    public int getMaxArraySize() {
        return maxArraySize;
    }

    /**
     * @description Maximum size of JSON arrays. For example, <code>[[1,2],[3,4,5]]</code> has a
     * array size of 2. The nested arrays have sizes of 2 and 3.
     * @default 1000
     * @param maxArraySize
     */
    @MCAttribute
    public void setMaxArraySize(int maxArraySize) {
        this.maxArraySize = maxArraySize;
    }

    public boolean isBlockProto() {
        return blockProto;
    }

    /**
     * @description Blocks JSON properties with a key of "__proto__" to avoid prototype pollution in Javascript backends.
     * @default true
     * @param blockProto
     */
    @MCAttribute
    public void setBlockProto(boolean blockProto) {
        this.blockProto = blockProto;
    }

    public OtherContentTypes getOtherContentTypes() {
        return otherContentTypes;
    }

    /**
     * @description What to do with content that is not JSON. This applies both to the body of a
     * non-JSON request and to the individual non-JSON parts of a multipart body, so
     * <code>skip</code> allows e.g. an image to be uploaded alongside a JSON document.
     * <p>Values: REJECT, SKIP</p>
     * @default REJECT
     * @example SKIP
     */
    @MCAttribute
    public void setOtherContentTypes(OtherContentTypes otherContentTypes) {
        this.otherContentTypes = otherContentTypes;
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
