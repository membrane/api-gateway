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
import java.util.*;

import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * @description Beautifies request and response bodies. Supported are the Formats: JSON, JSON5, XML, TEXT
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

            Charset charset = getCharset(msg, prettifier);
            msg.setBodyContent(prettifier.prettify(msg.getBody().getRaw(), charset));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return CONTINUE;
    }

    private static @Nullable Charset getCharset(Message msg, Prettifier prettifier) {
        if (msg.getCharset() != null)
            return Charset.forName(msg.getCharset());

        if (prettifier instanceof XMLPrettifier) {
            return null;
        }
        return UTF_8;
    }

    private static @NotNull Charset getEncoding(Message msg) {
        return Charset.forName(Objects.requireNonNullElseGet(msg.getCharset(), () -> UTF_8.name()));
    }

    private static Prettifier getPrettifier(Message msg) {
        return Prettifier.getInstance(msg.getHeader().getContentType());
    }

    @Override
    public String getShortDescription() {
        return "Pretty printing of message body. Applies, if the body is JSON, XML or text.";
    }
}
