/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client.rf;

import com.predic8.membrane.core.http.Response;
import jakarta.mail.internet.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class.getName());

    public static boolean isJson(Response g) throws ParseException {
        String contentType = g.getHeader().getFirstValue("Content-Type");
        if (contentType == null) return false;
        return g.getHeader().getContentTypeObject().match(APPLICATION_JSON);
    }

    public static String numberToString(Object number) {
        if (number == null) return null;
        if (number instanceof Integer in) return in.toString();
        if (number instanceof Long ln) return ln.toString();
        if (number instanceof Double) return number.toString();
        if (number instanceof String s) return s;
        log.warn("Unhandled number type " + number.getClass().getName());
        return null;
    }
}
