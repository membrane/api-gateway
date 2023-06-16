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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.TextUtil;

import java.io.IOException;
import java.io.InputStreamReader;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @description Beautifies request and response bodies. Supported are the Formats: JSON, XML
 * @topic 4. Interceptors/Features
 */
@MCElement(name="beautifier")
public class BeautifierInterceptor extends AbstractInterceptor{

    private final ObjectWriter ow = new ObjectMapper().writerWithDefaultPrettyPrinter();
    private final ObjectMapper om = new ObjectMapper();


    public BeautifierInterceptor() {
        name = "Beautifier";
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getRequest());
    }

    @Override
    public Outcome handleResponse(Exchange exc) throws Exception {
        return handleInternal(exc, exc.getResponse());
    }

    private Outcome handleInternal(Exchange exc, Message msg) throws IOException {
        if(msg.isJSON()) {
            msg.setBodyContent(
                    ow.writeValueAsBytes(
                            om.readTree(msg.getBodyAsStreamDecoded())
                    )
            );
            return CONTINUE;
        }
        if(msg.isXML()) {
            msg.setBodyContent(
                    TextUtil.formatXML(
                            new InputStreamReader(
                                    msg.getBodyAsStream(),
                                    msg.getHeader().getCharset()
                            )
                    ).getBytes(UTF_8)
            );
            return CONTINUE;
        }

        return CONTINUE;
    }

    @Override
    public String getShortDescription() {
        return "Pretty printing. Applies, if the body is JSON.";
    }
}
