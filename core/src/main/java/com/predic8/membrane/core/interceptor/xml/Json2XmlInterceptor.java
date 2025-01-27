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
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;


/**
 * @description Converts body payload from JSON to XML. The JSON must be an object other JSON documents e.g. arrays are not supported.
 * @explanation Resulting XML will be in UTF-8 encoding.
 * @topic 4. Interceptors/Features
 */
@MCElement(name = "json2Xml")
public class Json2XmlInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Json2XmlInterceptor.class);


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
            msg.setBodyContent(json2Xml(msg.getBodyAsStream()));
        } catch (JSONException e) {
            log.info("Error parsing JSON: {}",e.getMessage());
            user(router.isProduction(), getDisplayName())
                    .title("Error parsing JSON")
                    .addSubType("validation/json")
                    .exception(e)
                    .stacktrace(false)
                    .internal("flow", flow)
                    .internal("body", StringUtil.truncateAfter(msg.getBodyAsStringDecoded(), 200))
                    .buildAndSetResponse(exchange);
            return ABORT;
        }
        catch (Exception e) {
            internal(router.isProduction(), getDisplayName())
                    .title("Error parsing JSON")
                    .addSubType("validation/json")
                    .exception(e)
                    .stacktrace(true)
                    .internal("flow", flow)
                    .internal("body", StringUtil.truncateAfter(msg.getBodyAsStringDecoded(), 200))
                    .buildAndSetResponse(exchange);
            return ABORT;
        }

        msg.getHeader().setContentType(APPLICATION_XML);
        return CONTINUE;
    }

    private byte[] json2Xml(InputStream body) {
        return (PROLOG + XML.toString(getJSONRoot(body))).getBytes(UTF_8);
    }

    private @NotNull JSONObject getJSONRoot(InputStream body) {
        if (root != null) {
            return createRoot(root, convertToJsonObject(body));
        }
        JSONObject json = convertToJsonObject(body);

        // If there is exactly one element, then we can use that as root
        if (json.length() == 1) {
            return json;
        } else {
            // Otherwise we must wrap the fields into a single root element
            return createRoot("root", json);
        }
    }

    private JSONObject createRoot(String name, JSONObject jsonObject) {
        JSONObject root = new JSONObject();
        root.put(name, jsonObject);
        return root;
    }

    private JSONObject convertToJsonObject(InputStream body) {
        return new JSONObject(new JSONTokener(new InputStreamReader(body, UTF_8)));
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
     * A JSON object can have multiple keys. When transforming that to XML a single root element is needed.
     * If set a root element with this name will wrap the content.
     *
     * @param root Name of the element to wrap the content in
     */
    @MCAttribute
    public void setRoot(String root) {
        this.root = root;
    }
}
