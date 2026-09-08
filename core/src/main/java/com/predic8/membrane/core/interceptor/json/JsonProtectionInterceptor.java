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

import com.fasterxml.jackson.core.JsonParseException;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exceptions.ProblemDetails;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.protection.AbstractBodyProtectionInterceptor;
import com.predic8.membrane.core.interceptor.protection.Origin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.function.Supplier;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.isJson;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static java.util.EnumSet.of;

/**
 * @description <p>Enforces restrictions on JSON request bodies to protect against JSON-based attacks and resource exhaustion.
 * Validates against configurable limits to prevent attacks such as:</p>
 * <ul>
 *   <li>Deeply nested JSON structures (billion laughs attack)</li>
 *   <li>Memory exhaustion from oversized payloads</li>
 *   <li>Prototype pollution via __proto__ keys in JavaScript backends</li>
 *   <li>Duplicate key attacks ({"foo": 1, "foo": 2})</li>
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
public class JsonProtectionInterceptor extends AbstractBodyProtectionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JsonProtectionInterceptor.class);

    private JsonProtection scanner;
    private JsonLimits limits;

    private Boolean reportError;
    private int maxTokens = 10000;
    private int maxSize = 100 * 1024 * 1024;
    private int maxDepth = 50;
    private int maxStringLength = 262144;
    private int maxKeyLength = 256;
    private int maxObjectSize = 1000;
    private int maxArraySize = 1000;
    private boolean blockProto = true;

    public JsonProtectionInterceptor() {
        name = "json protection";
        setAppliedFlow(of(REQUEST));
    }

    @Override
    public void init() {
        super.init();
        limits = new JsonLimits(maxTokens, maxSize, maxDepth, maxStringLength,
                maxKeyLength, maxObjectSize, maxArraySize, blockProto);
        scanner = new JsonProtection(limits);
    }

    private boolean shouldProvideDetails() {
        if (reportError != null) {
            return reportError;
        }
        return !router.getConfiguration().isProduction();
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        try {
            return protect(exc, exc.getRequest());
        } catch (Exception e) {
            exc.setResponse(createErrorResponse(e.getMessage(), null, null));
            return ABORT;
        }
    }

    @Override
    protected boolean inspects(String contentType) {
        return isJson(contentType);
    }

    /**
     * Applied per document, so in a multipart body no single JSON part may exceed it - a large upload
     * does not have to be held in memory to be rejected.
     */
    @Override
    protected int maxDocumentSize() {
        return limits.sizeCeiling();
    }

    /**
     * Inspects a single content unit: either the whole body or one MIME part.
     */
    @Override
    protected Inspection inspectDocument(Exchange exc, Supplier<InputStream> document, Origin origin) {
        try {
            scanner.scan(document.get());
        } catch (JsonProtectionException e) {
            return reject(exc, origin.describe(e.getMessage()), e.getLine(), e.getCol());
        } catch (JsonParseException e) {
            return reject(exc, origin.describe(e.getMessage()),
                    e.getLocation().getLineNr(), e.getLocation().getColumnNr());
        } catch (Exception e) {
            return reject(exc, origin.describe(e.getMessage()), null, null);
        }
        return Inspection.passed();
    }

    private Inspection reject(Exchange exc, String msg, Integer line, Integer col) {
        exc.setResponse(createErrorResponse(msg, line, col));
        return Inspection.failed(ABORT);
    }

    @Override
    protected Outcome rejectOtherContentType(Exchange exc, Origin origin) {
        String msg = "Content-Type %s is not JSON. Set otherContentTypes to \"skip\" to pass non-JSON content through."
                .formatted(origin.contentType());
        exc.setResponse(createErrorResponse(origin.describe(msg), null, null));
        return ABORT;
    }

    @Override
    protected Outcome rejectUnprocessableBody(Exchange exc, Origin origin, String reason) {
        exc.setResponse(createErrorResponse(origin.describe(reason), null, null));
        return ABORT;
    }

    private Response createErrorResponse(String msg, Integer line, Integer col) {
        log.info("JSON protection violation. Line: {}, col: {}, msg: {}", line, col, msg);
        if (shouldProvideDetails()) {
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
     * <code>maxBodyLength</code> attribute. A value of <code>-1</code> disables the limit.
     * @default 104857600
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

    @Override
    public String getShortDescription() {
        return "Protects against several JSON attack classes.";
    }

    @Override
    public String getLongDescription() {
        return """
                <div>Enforces the following constraints:<br/><ul>\
                <li>HTTP request body must be well-formed JSON, unless the body is empty.</li>\
                <li>Limits the maximum number of tokens to %d. (Each string and opening bracket counts \
                as a token: <font style="font-family: monospace">{"a":"b"}</font> counts as 3 tokens)</li>\
                <li>Forbids duplicate keys. (<font style="font-family: monospace">{"a":"b", "a":"c"}</font> \
                will be rejected.)</li>\
                <li>Limits the total size in bytes of the body to %d.</li>\
                <li>Limits the maximum depth to %d. (<font style="font-family: monospace">{"a":[{"b"\
                :"c"}]}</font> has depth 3.)</li>\
                <li>Limits the maximum string length to %d. \
                (<font style="font-family: monospace">{"a":"abc"}</font> has max string length 3.)</li>\
                <li>Limits the maximum key length to %d. \
                (<font style="font-family: monospace">{"abc":"a"}</font> has key length 3.)</li>\
                <li>Limits the maximum object size to %d. \
                (<font style="font-family: monospace">{"a":"b","c":"d"}</font> has object size 2.)</li>\
                <li>Limits the maximum array size to %d. \
                (<font style="font-family: monospace">["a", "b"]</font> has array size 2.)</li>\
                </ul></div>"""
                .formatted(maxTokens, maxSize, maxDepth, maxStringLength,
                        Math.min(maxKeyLength, maxStringLength), maxObjectSize, maxArraySize);
    }
}
