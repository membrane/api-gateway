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
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.ReadingBodyException;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.json.JsonToXml;
import com.predic8.membrane.core.util.xml.schema.UndeclaredElementException;
import com.predic8.membrane.core.util.xml.schema.XmlSchemaMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.text.TextUtil.escapeControlCharsForLog;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description <p>Converts a JSON message body to XML. A body that is not JSON passes through unchanged. The result is
 * UTF-8 encoded, begins with an XML prolog, and the Content-Type is set to <code>application/xml</code>. If the body is
 * not valid JSON the exchange is aborted.</p>
 * <p>Given an XML Schema, the document follows the schema instead: the single key of the JSON
 * object names the root element, and the fields are written in the order and namespace the schema
 * declares.</p>
 * <pre>
 * json2Xml:
 *   [ root: &lt;element name&gt; ]
 *   [ array: &lt;element name&gt; ]       # default: array
 *   [ item: &lt;element name&gt; ]        # default: item
 *
 * json2Xml:
 *   xmlSchema: &lt;url or path&gt;       # replaces root, array and item
 * </pre>
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - json2Xml:
 *         root: order
 * </code></pre>
 * <p>See the tutorial tutorials/xml/10-JSON-to-XML.yaml.</p>
 */
@MCElement(name = "json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Json2XmlInterceptor.class);

    private static final String DEFAULT_ARRAY = "array";
    private static final String DEFAULT_ITEM = "item";

    // --- Configuration properties ---
    private String root;
    private String array = DEFAULT_ARRAY;
    private String item = DEFAULT_ITEM;
    private String xmlSchema;

    // --- Converter instance (created once) ---
    private JsonToXml converter;
    /** Built in init() when {@link #xmlSchema} is set; replaces {@link #converter}. */
    private volatile XmlSchemaMapping schemaMapping;

    @Override
    public void init() {
        super.init();
        if (xmlSchema != null) {
            rejectHeuristicSettings();
            schemaMapping = XmlSchemaMapping.load(router, getBeanBaseLocation(), xmlSchema);
            log.info("Loaded XML Schema {}, converting {} root element(s)", xmlSchema, schemaMapping.size());
            return;
        }
        converter = new JsonToXml().arrayName(array).itemName(item).rootName(root);

        // root gets handled dynamically at runtime because it depends on json document type
        // unless explicitly set via @MCAttribute
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc, REQUEST);
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc, RESPONSE);
    }

    /** The XML Schema decides the root and how arrays are written; a setting that would be ignored must not look effective. */
    private void rejectHeuristicSettings() {
        final var conflicting = new ArrayList<String>();
        if (root != null) conflicting.add("root");
        if (!DEFAULT_ARRAY.equals(array)) conflicting.add("array");
        if (!DEFAULT_ITEM.equals(item)) conflicting.add("item");
        if (conflicting.isEmpty()) return;
        throw new ConfigurationException("""
                json2Xml: %s cannot be combined with xmlSchema.
                With xmlSchema, the single key of the JSON object names the root element, and an array becomes repeated elements as the XML Schema declares them. Remove %s."""
                .formatted(String.join(", ", conflicting), conflicting.size() == 1 ? "it" : "them"));
    }

    private Outcome handleInternal(Exchange exchange, Flow flow) {
        Message msg = exchange.getMessage(flow);
        if (!msg.isJSON())
            return CONTINUE;

        final var mapping = schemaMapping;
        if (mapping != null)
            return convertWithSchema(exchange, flow, msg, mapping);

        final byte[] xml;
        try {
            xml = json2Xml(msg);
        } catch (ReadingBodyException e) {
            // FlowController answers a body that could not be read; this is not a conversion error.
            throw e;
        } catch (Exception e) {
            return conversionFailed(exchange, flow, e);
        }
        msg.setBodyContent(xml);
        msg.getHeader().setContentType(APPLICATION_XML);
        return CONTINUE;
    }

    private Outcome convertWithSchema(Exchange exc, Flow flow, Message msg, XmlSchemaMapping mapping) {
        if (msg.isBodyEmpty())
            return CONTINUE;
        final byte[] xml;
        try {
            xml = mapping.toXml(msg.getBodyAsStringDecoded());
        } catch (ReadingBodyException e) {
            // FlowController answers a body that could not be read; this is not a conversion error.
            throw e;
        } catch (UndeclaredElementException e) {
            return undeclaredElement(exc, flow, e);
        } catch (Exception e) {
            return conversionFailed(exc, flow, e);
        }
        msg.setBodyContent(xml);
        msg.getHeader().setContentType(APPLICATION_XML);
        return CONTINUE;
    }

    private Outcome undeclaredElement(Exchange exc, Flow flow, UndeclaredElementException e) {
        final var element = escapeControlCharsForLog(e.getElement().toString());
        log.info("JSON {} not converted: {}", flowNoun(flow), escapeControlCharsForLog(e.getMessage()));
        final var production = router.getConfiguration().isProduction();
        if (flow == REQUEST) {
            // The client sent the key, so naming it tells the client nothing it does not know.
            user(production, getDisplayName())
                    .flow(flow)
                    .title("Root element not declared by the XML Schema")
                    .detail("The JSON names the root element %s, which the XML Schema does not declare.".formatted(element), true)
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
        log.info("Could not convert JSON {} to XML: {}", flowNoun(flow), reason);
        log.debug("", e);
        final var production = router.getConfiguration().isProduction();
        (flow == REQUEST ? user(production, getDisplayName()) : internal(production, getDisplayName()))
                .flow(flow)
                // Fixed text the client that sent the request may see; a response error stays hidden.
                .detail("The JSON %s could not be converted to XML.".formatted(flowNoun(flow)), flow == REQUEST)
                .internal("reason", reason)
                .stacktrace(false)
                .buildAndSetResponse(exc);
        return ABORT;
    }

    private static String flowNoun(Flow flow) {
        return flow == REQUEST ? "request" : "response";
    }

    private byte[] json2Xml(Message msg) {
        return converter.toXml(msg.getBodyAsStringDecoded()).getBytes(UTF_8);
    }

    @Override
    public String getDisplayName() {
        return "json 2 xml";
    }

    @Override
    public String getShortDescription() {
        return "Converts JSON message bodies to XML.";
    }

    public String getRoot() {
        return root;
    }

    /**
     * @description Name of the single XML root element the converted document is wrapped in. When set, every result is
     * wrapped in an element with this name. When unset, the name is derived from the document: a single-property object
     * uses that property as the root, a top-level array uses the array element name, and any other object or value is
     * wrapped in <code>root</code>.
     * @default derived from the JSON
     * @example order
     */
    @MCAttribute
    public void setRoot(String root) {
        this.root = root;
    }

    public String getArray() {
        return array;
    }

    /**
     * @description XML element name wrapped around every JSON array, nested ones included.
     * @default array
     * @example items
     */
    @MCAttribute
    public void setArray(String array) {
        this.array = array;
    }

    public String getItem() {
        return item;
    }

    /**
     * @description XML element name used for each item of a JSON array.
     * @default item
     * @example entry
     */
    @MCAttribute
    public void setItem(String item) {
        this.item = item;
    }

    public String getXmlSchema() {
        return xmlSchema;
    }

    /**
     * @description An XML Schema (XSD) that drives the mapping: a URL or a file path, relative to the
     * configuration file. Its imports and includes are loaded too. The JSON must be an object with a
     * single key naming a global element of the schema, e.g. <code>{"books": {...}}</code>. Fields
     * are written in the order the schema declares them and in their namespace, an array becomes
     * repeated elements, <code>@</code>-prefixed keys become attributes, <code>$value</code> becomes
     * the element's text, and <code>null</code> becomes <code>xsi:nil="true"</code>. Invalid JSON, or a
     * root key the schema does not declare, is rejected with <code>400</code> in the request flow and
     * <code>500</code> in the response flow. Cannot be combined with <code>root</code>,
     * <code>array</code> or <code>item</code>.
     * @example books.xsd
     */
    @MCAttribute
    public void setXmlSchema(String xmlSchema) {
        this.xmlSchema = xmlSchema;
    }
}