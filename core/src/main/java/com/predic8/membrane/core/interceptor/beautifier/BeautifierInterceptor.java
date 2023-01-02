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

package com.predic8.membrane.core.interceptor.beautifier;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;

import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.stream.*;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;

/**
 * @description Beautifies request and response bodies. Supported are the Formats: JSON
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
        if(!msg.isJSON()){
            return CONTINUE;
        }
        msg.setBodyContent(ow.writeValueAsBytes(om.readTree(msg.getBodyAsStream())));
        return CONTINUE;
    }
}
