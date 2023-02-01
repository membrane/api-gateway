/*
 *  Copyright 2022 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.util;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;
import static com.predic8.membrane.core.util.HttpUtil.htmlMessage;

public class ErrorUtil {

    private static final Logger log = LoggerFactory.getLogger(ErrorUtil.class.getName());

    public static ObjectMapper om = new ObjectMapper(); // GSON ?

    public static void createAndSetErrorResponse(Exchange exc, int statusCode, String message) {
        Response.ResponseBuilder builder = Response.ResponseBuilder.newInstance().
                status(statusCode, getMessageForStatusCode(statusCode));
        if (requestsAcceptsHtmlMimeType(exc)) {
            builder
                    .contentType(TEXT_HTML_UTF8)
                    .body(htmlMessage(getMessageForStatusCode(statusCode), message)).build();
        } else {
            Map<String,String> json = new HashMap<>();
            json.put("error",message);
            try {
                builder
                        .contentType(APPLICATION_JSON)
                        .body(om.writeValueAsBytes(json))
                        .build();
            } catch (JsonProcessingException e) {
                log.error("Should never happen!");
            }
        }
        exc.setResponse(builder.build());
    }

    private static boolean requestsAcceptsHtmlMimeType(Exchange exc) {
        return exc.getRequest().getHeader().getAccept() != null &&  exc.getRequest().getHeader().getAccept().contains("html");
    }
}
