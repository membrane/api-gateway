package com.predic8.membrane.core.util;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.slf4j.*;

import java.util.*;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.TEXT_HTML_UTF8;
import static com.predic8.membrane.core.util.HttpUtil.getMessageForStatusCode;
import static com.predic8.membrane.core.util.HttpUtil.htmlMessage;

public class ErrorUtil {

    private static Logger log = LoggerFactory.getLogger(ErrorUtil.class.getName());

    public static ObjectMapper om = new ObjectMapper(); // GSON ?

    public static void createAndSetErrorResponse(Exchange exc, int statusCode, String message) {
        Response.ResponseBuilder builder = Response.ResponseBuilder.newInstance().
                status(statusCode, getMessageForStatusCode(statusCode));
        if (exc.getRequest().getHeader().getAccept() != null &&  exc.getRequest().getHeader().getAccept().contains("html")) {
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
}
