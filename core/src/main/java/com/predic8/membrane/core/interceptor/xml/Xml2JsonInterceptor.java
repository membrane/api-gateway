/* Copyright 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.xml;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.xml.XMLEncodingUtil;
import org.apache.commons.io.ByteOrderMark;
import org.json.XML;
import org.json.XMLParserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.util.MessageUtil.getContent;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.json.XMLParserConfiguration.ORIGINAL;

/**
 * @description
 * <p>Converts an XML message body to JSON using a structural, heuristic mapping: elements and
 * attributes both become JSON properties, and a repeated element becomes a JSON array while the
 * same element occurring once becomes an object.</p>
 *
 * <p>Element order, mixed content, and namespaces
 * are not preserved. Only messages with an XML content type are converted; other bodies pass
 * through unchanged.</p>
 *
 * <p>Malformed XML aborts the exchange with a Problem Details response
 * (<code>400</code> in the request flow, <code>500</code> in the response flow).</p>
 *
 * <p>The character
 * encoding is taken from the <code>Content-Type</code> header if present, otherwise read from
 * the XML prolog, and the result is always UTF-8 encoded JSON.</p>
 *
 * <p>For contract-driven integrations
 * that need a fixed output shape, a template, XSLT, or schema-aware mapping gives more control.
 * See tutorials/xml/20-XML-to-JSON.yaml.</p>
 * <pre>
 * xml2Json:
 *   [ keepString: true | false ]                 # default: false
 *   [ convertNilAttributeToNull: true | false ]   # default: true
 *   forceList:                                    # 0..*; always render these element names as arrays
 *     - &lt;element-name&gt;
 *     ...
 * </pre>
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - xml2Json: {}
 * </code></pre>
 */
@MCElement(name = "xml2Json")
public class Xml2JsonInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Xml2JsonInterceptor.class);

    private boolean keepString = false;
    private boolean convertNilAttributeToNull = true;
    private final List<String> forceList = new ArrayList<>();
    private volatile XMLParserConfiguration xmlParserConfig;

    @Override
    public void init() {
        xmlParserConfig = buildParserConfig();
        super.init();
    }

    @Override
    public String getShortDescription() {
        return "Converts XML message bodies to JSON.";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    private Outcome handleInternal(Exchange exc, Flow flow) {
        Message msg = exc.getMessage(flow);
        if (!msg.isXML()) {
            return CONTINUE;
        }
        try {
            msg.setBodyContent(xml2json(getBodyAsString(msg)));
            msg.getHeader().setContentType(APPLICATION_JSON_UTF8);
            return CONTINUE;
        } catch (UnsupportedEncodingException e) {
            handleException(exc, flow, e, "Unsupported encoding: " + e.getMessage());
        } catch (Exception e) {
            handleException(exc, flow, e, null);
        }
        return ABORT;
    }

    private static String getBodyAsString(Message msg) throws IOException {
        // Conversion is expensive but needed to get encoding from XML
        // because org.json.XML ignores the encoding specified in the XML prolog
        byte[] body = getContent(msg);

        // A byte order mark identifies the byte stream's encoding directly, so it wins over a
        // declared charset - the same precedence every other XML consumer gets through
        // XMLInputSourceUtil. The mark itself is dropped: org.json.XML would take it for content.
        ByteOrderMark bom = XMLEncodingUtil.getByteOrderMark(body);
        if (bom != null)
            return new String(body, bom.length(), body.length - bom.length(), bom.getCharsetName());

        if (msg.getHeader().getCharset() != null) return msg.getBodyAsStringDecoded();

        var fromProlog = XMLEncodingUtil.getEncodingFromXMLProlog(body);
        return new String(body, fromProlog != null ? fromProlog : UTF_8.name());
    }


    private byte[] xml2json(String xml) {
        // In org.json.XML the encoding is skipped, so xml encoding is always ignored: x.skipPast("?>");
        return XML.toJSONObject(xml, xmlParserConfig).toString().getBytes(UTF_8);
    }

    private void handleException(Exchange exc, Flow flow, Exception e, String msg) {
        if (msg == null) {
            msg = "Could not transform XML to JSON: " + e.getMessage();
            log.info(msg, e);
            log.debug("", e);
        }
        internal(router.getConfiguration().isProduction(), getDisplayName()).flow(flow).status(400)
                .detail(msg)
                .exception(e)
                .topLevel("charset-from-header", exc.getMessage(flow).getHeader().getCharset())
                .stacktrace(false)
                .buildAndSetResponse(exc);
    }

    private XMLParserConfiguration buildParserConfig() {
        XMLParserConfiguration cfg = ORIGINAL
                .withKeepStrings(keepString)
                .withConvertNilAttributeToNull(convertNilAttributeToNull);

        if (!forceList.isEmpty())
            cfg = cfg.withForceList(new HashSet<>(forceList));

        return cfg;
    }

    @Override
    public String getDisplayName() {
        return "xml 2 json";
    }


    public boolean isKeepString() {
        return keepString;
    }

    /**
     * @description
     * If <code>true</code>, keeps every element's text as a JSON string instead of coercing
     * numeric- or boolean-looking text into JSON numbers and booleans.
     * @default false
     * @example true
     */
    @MCAttribute
    public void setKeepString(boolean keepString) {
        this.keepString = keepString;
    }

    public boolean isConvertNilAttributeToNull() {
        return convertNilAttributeToNull;
    }

    /**
     * @description
     * If <code>true</code>, converts elements marked <code>xsi:nil="true"</code> into JSON
     * <code>null</code> values.
     * @default true
     * @example false
     */
    @MCAttribute
    public void setConvertNilAttributeToNull(boolean convertNilAttributeToNull) {
        this.convertNilAttributeToNull = convertNilAttributeToNull;
    }

    public List<String> getForceList() {
        return forceList;
    }

    /**
     * @description
     * Element names that are always rendered as a JSON array, even where they occur only once.
     * Without this, whether an element becomes an array depends on how many times it occurs in a
     * given message, so its JSON type can change from request to request. Names are matched
     * literally against the parsed tag, prefix included, e.g. <code>ns:customer</code> for a
     * namespaced element.
     * @example [customer, product]
     */
    @MCChildElement(allowForeign = true)
    public void setForceList(List<String> forceList) {
        this.forceList.addAll(forceList);
    }
}