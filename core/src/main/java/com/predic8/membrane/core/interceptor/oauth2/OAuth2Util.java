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
import com.predic8.membrane.core.http.*;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.regex.Pattern;

public class OAuth2Util {

    public static String urlencode(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, "UTF-8").replaceAll("\\+", "%20");
    }

    public static String urldecode(String value) throws UnsupportedEncodingException {
        return URLDecoder.decode(value, "UTF-8").replaceAll("\\+", "%20");
    }

    private static void removeDuplicateSessionValues(HeaderField header) {
        HashMap<String,String> uniqueValues = new HashMap<String, String>();
        String[] values = header.getValue().split(Pattern.quote(";"));

        for(String value : values){
            String[] temp = value.split(Pattern.quote("="));

            if(!uniqueValues.containsKey(temp[0]))
                uniqueValues.put(temp[0].trim(), createSessionValue(temp));
        }

        header.setValue(buildSessionHeaderValue(uniqueValues));
    }

    private static String buildSessionHeaderValue(HashMap<String, String> uniqueValues) {
        StringBuilder builder = new StringBuilder();
        for(String key : uniqueValues.keySet())
            builder.append(";").append(key).append("=").append(uniqueValues.get(key));
        builder.deleteCharAt(0);
        return builder.toString();
    }

    private static String createSessionValue(String[] temp) {
        String param = "";
        for(int i = 1; i < temp.length;i++)
            param += temp[i] + "=";
        param = param.substring(0,param.length()-1);
        return param.trim();
    }

    public static Message addSessionHeader(Message msg, HeaderField session) {
        msg.getHeader().add(session);
        return msg;
    }

    public static boolean isOpenIdScope(String scope) {
        if (scope != null && !scope.isEmpty() && scope.contains("openid")) {
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

    public static Response createParameterizedJsonErrorResponse(Exchange exc, ReusableJsonGenerator jsonGen, String... params) throws IOException {
        if (params.length % 2 != 0)
            throw new IllegalArgumentException("The number of strings passed as params is not even");
        String json;
        synchronized (jsonGen) {
            JsonGenerator gen = jsonGen.resetAndGet();
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
}
