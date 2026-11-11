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
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.xml.schema.UndeclaredElementException;
import com.predic8.membrane.core.util.xml.schema.XmlSchemaMapping;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.xml.XMLEncodingUtil;
import com.predic8.membrane.core.util.xml.XMLInputSourceUtil;
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
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.util.MessageUtil.getContent;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON_UTF8;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;
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
 * <p>Given an XML Schema, the mapping follows the schema instead of the heuristics, so the JSON
 * has a fixed shape: values are typed as declared, an element that may repeat is always an array,
 * and a document the schema does not declare is rejected. See tutorials/xml/20-XML-to-JSON.yaml
 * and tutorials/xml/25-XML-to-JSON-with-XSD.yaml.</p>
 * <pre>
 * xml2Json:
 *   [ keepString: true | false ]                 # default: false
 *   [ convertNilAttributeToNull: true | false ]   # default: true
 *   forceList:                                    # 0..*; always render these element names as arrays
 *     - &lt;element-name&gt;
 *     ...
 *
 * xml2Json:
 *   xmlSchema: &lt;url or path&gt;                   # replaces the heuristic settings above
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
    private String xmlSchema;
    private volatile XMLParserConfiguration xmlParserConfig;
    /** Built in init() when {@link #xmlSchema} is set; replaces the heuristic mapping. */
    private volatile XmlSchemaMapping schemaMapping;

    @Override
    public void init() {
        if (xmlSchema != null) {
            rejectHeuristicSettings();
            schemaMapping = XmlSchemaMapping.load(router, getBeanBaseLocation(), xmlSchema);
            log.info("Loaded XML Schema {}, converting {} root element(s)", xmlSchema, schemaMapping.size());
        } else {
            xmlParserConfig = buildParserConfig();
        }
        super.init();
    }

    /** The XML Schema decides types, arrays and nil values; a setting that would be ignored must not look effective. */
    private void rejectHeuristicSettings() {
        final var conflicting = new ArrayList<String>();
        if (keepString) conflicting.add("keepString");
        if (!convertNilAttributeToNull) conflicting.add("convertNilAttributeToNull");
        if (!forceList.isEmpty()) conflicting.add("forceList");
        if (conflicting.isEmpty()) return;
        throw new ConfigurationException("""
                xml2Json: %s cannot be combined with xmlSchema.
                With xmlSchema, the XML Schema decides which values are numbers or booleans, which elements are arrays, and which are null. Remove %s."""
                .formatted(String.join(", ", conflicting), conflicting.size() == 1 ? "it" : "them"));
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
        final var mapping = schemaMapping;
        if (mapping != null) {
            return convertWithSchema(exc, flow, msg, mapping);
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

    private Outcome convertWithSchema(Exchange exc, Flow flow, Message msg, XmlSchemaMapping mapping) {
        if (msg.isBodyEmpty()) {
            return CONTINUE;
        }
        final byte[] json;
        try (var body = msg.getBodyAsStreamDecoded()) {
            json = mapping.toJson(XMLInputSourceUtil.getInputSource(body, msg.getHeader().getCharset()));
        } catch (ReadingBodyException e) {
            // FlowController answers a body that could not be read; this is not a conversion error.
            throw e;
        } catch (UndeclaredElementException e) {
            return undeclaredElement(exc, flow, e);
        } catch (Exception e) {
            return conversionFailed(exc, flow, e);
        }
        msg.setBodyContent(json);
        msg.getHeader().setContentType(APPLICATION_JSON_UTF8);
        return CONTINUE;
    }

    private Outcome undeclaredElement(Exchange exc, Flow flow, UndeclaredElementException e) {
        final var element = escapeControlCharsForLog(e.getElement().toString());
        log.info("XML {} not converted: {}", flowNoun(flow), escapeControlCharsForLog(e.getMessage()));
        final var production = router.getConfiguration().isProduction();
        if (flow == REQUEST) {
            // The client sent the element, so naming it tells the client nothing it does not know.
            user(production, getDisplayName())
                    .flow(flow)
                    .title("Document not declared by the XML Schema")
                    .detail("The document element %s is not declared by the XML Schema.".formatted(element), true)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        } else {
            // The backend is not trusted: what it sent stays internal.
            internal(production, getDisplayName())
                    .flow(flow)
                    .detail("The response of the backend does not match the XML Schema.")
                    .internal("element", element)
                    .stacktrace(false)
                    .buildAndSetResponse(exc);
        }
        return ABORT;
    }

    private Outcome conversionFailed(Exchange exc, Flow flow, Exception e) {
        final var reason = escapeControlCharsForLog(String.valueOf(e.getMessage()));
        log.info("Could not convert XML {} to JSON: {}", flowNoun(flow), reason);
        log.debug("", e);
        final var production = router.getConfiguration().isProduction();
        (flow == REQUEST ? user(production, getDisplayName()) : internal(production, getDisplayName()))
                .flow(flow)
                .detail("The XML %s could not be converted to JSON.".formatted(flowNoun(flow)))
                .internal("reason", reason)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private static String flowNoun(Flow flow) {
        return flow == REQUEST ? "request" : "response";
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
        final var production = router.getConfiguration().isProduction();
        // The client sent the XML of a request, so it is told that its XML is the problem. What a
        // backend sent stays internal.
        final var problem = flow == REQUEST
                ? user(production, getDisplayName()).title("Invalid XML")
                        .detail("The XML request could not be converted to JSON.", true)
                : internal(production, getDisplayName())
                        .detail("The XML response could not be converted to JSON.");
        problem.flow(flow)
                .internal("reason", msg)
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

    public String getXmlSchema() {
        return xmlSchema;
    }

    /**
     * @description An XML Schema (XSD) that drives the mapping instead of the heuristics: a URL or a
     * file path, relative to the configuration file. Its imports and includes are loaded too.
     * Values are typed as the schema declares them, an element that may repeat is always an array,
     * and <code>xsi:nil</code> becomes <code>null</code>. The result has the root element as its single
     * key, but the structure differs from the heuristic mapping: attributes become
     * <code>@</code>-prefixed keys, the text of an element with attributes is put under
     * <code>$value</code>, and keys are local names without a prefix. A document whose root element
     * the schema does not declare globally is rejected with <code>400</code> in the request flow and
     * <code>500</code> in the response flow. Cannot be combined with <code>keepString</code>,
     * <code>convertNilAttributeToNull</code> or <code>forceList</code>.
     * @example books.xsd
     */
    @MCAttribute
    public void setXmlSchema(String xmlSchema) {
        this.xmlSchema = xmlSchema;
    }
}