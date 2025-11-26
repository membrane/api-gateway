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
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.json.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.util.JsonUtil.*;
import static com.predic8.membrane.core.util.StringUtil.*;
import static java.nio.charset.StandardCharsets.*;


/**
 * @description Converts the body payload from JSON to XML. The JSON document must be an object or array.
 * @explanation Resulting XML will be in UTF-8 encoding.
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    // Prolog is needed to provide the UTF-8 encoding
    private static final String PROLOG = """
            <?xml version="1.0" encoding="UTF-8"?>""";

    private String root;

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

    private byte[] json2Xml(Message body) throws IOException {
        return (PROLOG + XML.toString(getBodyAsXML(body))).getBytes(UTF_8);
    }

    private @NotNull Object getBodyAsXML(Message msg) throws IOException {

        Object obj = getJSONObject(msg, detectJsonType(msg.getBodyAsStreamDecoded()));

        if (obj instanceof JSONObject jsonObject) {
            if (root != null) {
                return wrapWithRoot(root, jsonObject);
            }
            if (jsonObject.length() == 1) {
                return jsonObject;
            }
            // Otherwise we must wrap the fields into a single root element
            return wrapWithRoot("root", jsonObject);
        }

        if (obj instanceof JSONArray) {
            JSONObject wrapper = new JSONObject();
            wrapper.put("item",obj);
            return wrapWithRoot(root != null ? root: "array", wrapper);
        }

        // Should never reach here as getJSONObject only returns JSONObject or JSONArray
        throw new RuntimeException("Error parsing JSON");
    }

    private static Object getJSONObject(Message msg, JsonUtil.JsonType type) {
        return switch (type) {
            case OBJECT -> new JSONObject(new JSONTokener(msg.getBodyAsStreamDecoded()));
            case ARRAY -> new JSONArray(new JSONTokener(msg.getBodyAsStreamDecoded()));
            case UNKNOWN -> throw new IllegalArgumentException("Body is not a valid JSON document.");
            default ->
                    throw new IllegalArgumentException("%s as JSON document is not supported. Use object or array.".formatted(type));
        };
    }

    private JSONObject wrapWithRoot(String name, Object node) {
        JSONObject root = new JSONObject();
        root.put(name, node);
        return root;
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
     *
     * @param root Name of the element to wrap the content in
     * @default "root" for objects and "array" for arrays
     */
    @MCAttribute
    public void setRoot(String root) {
        this.root = root;
    }
}
