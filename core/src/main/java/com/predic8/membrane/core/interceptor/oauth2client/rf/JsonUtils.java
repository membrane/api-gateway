package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.http.Response;
import jakarta.mail.internet.ParseException;

import java.util.Optional;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

public class JsonUtils {
    public static boolean isJson(Response response) throws ParseException {
        String contentType = response.getHeader().getFirstValue("Content-Type");

        if (contentType == null)
            return false;

        return response.getHeader().getContentTypeObject().match(APPLICATION_JSON);
    }
}
