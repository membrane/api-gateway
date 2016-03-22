/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.HeaderName;
import com.predic8.membrane.core.http.Message;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class OAuth2Util {

    public static String urlencode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8").replaceAll("\\+", "%20");
    }

    public static String urldecode(String value) throws UnsupportedEncodingException {
        return URLDecoder.decode(value, "UTF-8").replaceAll("\\+", "%20");
    }

    public static void extractSessionFromRequestAndAddToResponse(Exchange exc) {
        addSessionHeader(exc.getResponse(), extraxtSessionHeader(exc.getRequest()));
    }

    public static HeaderField extraxtSessionHeader(Message msg) {
        for (HeaderField h : msg.getHeader().getAllHeaderFields()) {
            if (h.getHeaderName().equals("Set-Cookie")) {
                return h;
            } else if (h.getHeaderName().equals("Cookie")) {
                h.setHeaderName(new HeaderName("Set-Cookie"));
                return h;
            }
        }
        throw new RuntimeException();
    }

    public static Message addSessionHeader(Message msg, HeaderField session) {
        msg.getHeader().add(session);
        return msg;
    }

    public static boolean isOpenIdScope(String scope) {
        if (scope.contains("openid")) {
            String[] split = scope.split(" ");
            for (String singleScope : split)
                if (singleScope.equals("openid"))
                    return true;
        }
        return false;
    }

    public static boolean isAbsoluteUri(String uri) {
        return uri.contains("://");
    }
}
