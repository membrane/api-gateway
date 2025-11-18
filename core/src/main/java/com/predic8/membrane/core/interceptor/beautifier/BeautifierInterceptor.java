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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.prettifier.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.nio.charset.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Beautifies request and response bodies. Supported are the Formats: <b>JSON</b>, <b>JSON5</b>, <b>XML</b>, <b>TEXT</b>
 * @topic 2. Enterprise Integration Patterns
 */
@MCElement(name = "beautifier")
public class BeautifierInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BeautifierInterceptor.class);

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
        try {
            if (msg.isBodyEmpty())
                return CONTINUE;
        } catch (IOException e) {
            log.error("", e);
            return CONTINUE;
        }

        try {
            Prettifier prettifier = getPrettifier(msg);
            // Shortcut not to avoid reading bytes in NullPrettifier
            if (prettifier instanceof NullPrettifier)
                return CONTINUE;
            msg.setBodyContent(prettifier.prettify(msg.getBodyAsStreamDecoded(), getCharset(msg, prettifier)));
        } catch (IOException e) {
            // If it is not possible to beautify, to nothing
            // Cause will be often user input => do not log stacktrace
            log.info("Could not beautify message body: {}", e.getMessage());
        }

        return CONTINUE;
    }

    private static @Nullable Charset getCharset(Message msg, Prettifier prettifier) {
        // XML is fine with no charset cause the XML prolog may contain an encoding
        if (msg.getHeader().getCharset() == null && prettifier instanceof XMLPrettifier)
            return null;

        return msg.getCharsetOrDefault();
    }

    private static Prettifier getPrettifier(Message msg) {
        return Prettifier.getInstance(msg.getHeader().getContentType());
    }

    @Override
    public String getShortDescription() {
        return "Pretty printing of message bodies. Can format JSON, JSON5, XML or text.";
    }
}
