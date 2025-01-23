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

import com.fasterxml.jackson.core.JsonGenerator;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.MimeType;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static com.predic8.membrane.core.http.Header.X_FORWARDED_PROTO;

public class OAuth2Util {

    public static String urlencode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    public static String urldecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    public static boolean isOpenIdScope(String scope) {
        if (scope == null) {
            return false;
        }
        return Arrays.asList(scope.split(" ")).contains("openid");
    }

    public static boolean isAbsoluteUri(String uri) {
        return uri.contains("://");
    }

    public static Response createParameterizedJsonErrorResponse(String... params) throws IOException {
        if (params.length % 2 != 0)
            throw new IllegalArgumentException("The number of strings passed as params is not even");

        String json;
        BufferedJsonGenerator jsonGen = new BufferedJsonGenerator();
        try (JsonGenerator gen = jsonGen.jg()) {
            gen.writeStartObject();
            for (int i = 0; i < params.length; i += 2)
                gen.writeObjectField(params[i], params[i + 1]);
            gen.writeEndObject();
            json = jsonGen.getJson();
        }

        return Response.badRequest()
                .body(json)
                .contentType(MimeType.APPLICATION_JSON_UTF8)
                .dontCache()
                .build();
    }

    public static @NotNull String getPublicURL(Exchange exc) {
        return (isHTTPS(exc.getProxy(), getxForwardedProto(exc)) ? "https://" : "http://") + exc.getOriginalHostHeader();
    }

    private static boolean isHTTPS(Proxy proxy, String xForwardedProto) {
        if (!(proxy instanceof SSLableProxy sp)) {
            return false;
        }
        return xForwardedProto != null ? "https".equals(xForwardedProto) : sp.isInboundSSL();
    }

    private static String getxForwardedProto(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(X_FORWARDED_PROTO);
    }
}
