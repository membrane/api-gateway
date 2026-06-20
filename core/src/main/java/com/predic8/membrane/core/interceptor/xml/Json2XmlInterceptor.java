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
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.json.JsonToXml;

import static com.predic8.membrane.core.exceptions.ProblemDetails.internal;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_XML;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.util.text.StringUtil.truncateAfter;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Converts a JSON message body to XML. A body that is not JSON passes through unchanged. The result is
 * UTF-8 encoded, begins with an XML prolog, and the Content-Type is set to <code>application/xml</code>. If the body is
 * not valid JSON the exchange is aborted with a 500 Problem Details. See the examples under
 * examples/message-transformation/json2xml and the tutorial tutorials/xml/10-JSON-to-XML.yaml.
 * @topic 2. Enterprise Integration Patterns
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - json2Xml:
 *         root: order
 * </code></pre>
 */
@MCElement(name = "json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    // --- Configuration properties ---
    private String root;
    private String array = "array";
    private String item = "item";

    // --- Converter instance (created once) ---
    private JsonToXml converter;

    @Override
    public void init() {
        super.init();
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

    private Outcome handleInternal(Exchange exchange, Flow flow) {
        Message msg = exchange.getMessage(flow);
        if (!msg.isJSON())
            return CONTINUE;

        try {
            msg.setBodyContent(json2Xml(msg));
        } catch (Exception e) {
            internal(router.getConfiguration().isProduction(), getDisplayName())
                    .title("Error parsing JSON")
                    .addSubType("validation/json")
                    .exception(e) // Message contains a meaningful error message
                    .stacktrace(false)
                    .internal("flow", flow)
                    .internal("body", truncateAfter(msg.getBodyAsStringDecoded(), 200))
                    .buildAndSetResponse(exchange);
            return Outcome.ABORT;
        }

        msg.getHeader().setContentType(APPLICATION_XML);
        return CONTINUE;
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
}