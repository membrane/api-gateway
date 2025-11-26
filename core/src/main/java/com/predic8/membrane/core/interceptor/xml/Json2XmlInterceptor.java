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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.util.json.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.util.StringUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Converts JSON message bodies into XML.
 * The converter wraps the JSON document into a root element. The name of the
 * root element is configurable. If unset, JSON objects default to "root" and JSON arrays default to "array".
 *
 * @explanation
 * This interceptor reads the JSON body, converts it into XML and updates the message
 * body and Content-Type header. The resulting XML is always UTF-8 encoded and starts with an XML prolog.
 *
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    // Prolog is needed to provide the UTF-8 encoding
    private static final String PROLOG = """
            <?xml version="1.0" encoding="UTF-8"?>""";

    // --- Configuration properties ---
    private String root;
    private String array = "array";
    private String item = "item";

    // --- Converter instance (created once) ---
    private JsonToXmlListStyle converter;

    @Override
    public void init() {
        super.init();
        converter = new JsonToXmlListStyle();

        converter.setArrayName(array);
        converter.setItemName(item);
        converter.setRootName(root);

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
            internal(router.isProduction(), getDisplayName())
                    .title("Error parsing JSON")
                    .addSubType("validation/json")
                    .exception(e) // Message contains a meaningful error message
                    .stacktrace(false)
                    .internal("flow", flow)
                    .internal("body", truncateAfter(msg.getBodyAsStringDecoded(), 200))
                    .buildAndSetResponse(exchange);
            return ABORT;
        }

        msg.getHeader().setContentType(APPLICATION_XML);
        return CONTINUE;
    }

    private byte[] json2Xml(Message msg) {
        return (PROLOG + converter.toXml(msg.getBodyAsStringDecoded())).getBytes(UTF_8);
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
     * XML always needs a single root element. A JSON object can have multiple properties or an array can have multiple items.
     * The converter therefore wraps the document into a root element if necessary. With this property you can set the name of the root element.
     * If the property is set, the XML is always wrapped into an element with the given name.
     *
     * @param root Name of the element to wrap the content in
     * @default "root" for objects and "array" for arrays
     */
    @MCAttribute
    public void setRoot(String root) {
        this.root = root;
    }

    /**
     * Sets the XML tag name used to represent JSON arrays.
     * @default "array"
     */
    @MCAttribute
    public void setArray(String array) {
        this.array = array;
        if (converter != null)
            converter.setArrayName(array);
    }

    public String getItem() {
        return item;
    }

    /**
     * Sets the XML tag name used for array items.
     * Default is "item".
     */
    @MCAttribute
    public void setItem(String item) {
        this.item = item;
        if (converter != null)
            converter.setItemName(item);
    }

}
