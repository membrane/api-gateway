/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.beautifier;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.io.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.util.TextUtil.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Beautifies request and response bodies. Supported are the Formats: JSON, XML
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "beautifier")
public class BeautifierInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BeautifierInterceptor.class);

    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final ObjectMapper om = new ObjectMapper();


    public BeautifierInterceptor() {
        name = "beautifier";
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        return handleInternal(exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) {
        return handleInternal(exc.getResponse());
    }

    private Outcome handleInternal(Message msg) {
        if (msg.isJSON()) {
            beautifyJSON(msg);
            return CONTINUE;
        }
        if (msg.isXML()) {
            beautifyXML(msg);
        }
        return CONTINUE;
    }

    /**
     * If it is not possible to beautify, leave body as it is.
     */
    private static void beautifyXML(Message msg) {
        try {
            InputStreamReader reader = new InputStreamReader(msg.getBodyAsStream(), msg.getHeader().getCharset());
            msg.setBodyContent(formatXML(reader).getBytes(UTF_8));
        } catch (Exception e) {
            // If it is not possible to beautify, to nothing
            log.warn("Error parsing XML: {}", e.getMessage());
        }
    }

    /**
     * If it is not possible to beautify, leave body as it is.
     */
    private void beautifyJSON(Message msg) {
        try {
            JsonNode node = om.readTree(msg.getBodyAsStreamDecoded());
            msg.setBodyContent(ow.writeValueAsBytes(node));
        } catch (IOException e) {
            // If it is not possible to beautify, to nothing
            log.warn("Error parsing JSON: {}", e.getMessage());
        }
    }

    @Override
    public String getShortDescription() {
        return "Pretty printing. Applies, if the body is JSON.";
    }
}
