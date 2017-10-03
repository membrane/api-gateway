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

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Pattern;

public class CookieUtil {

    public static String stripAndExtractCookiesSorted(Exchange exc, String ownCookieName) throws Exception {
        String[] cookies = getCookieHeaderSplit(exc);
        if(cookies == null)
            throw new Exception("Cookie is missing");

        ArrayList<String> newCookies = removeOwnCookieNameFromCookieHeader(ownCookieName, cookies);

        Collections.sort(newCookies);

        StringBuilder builder = new StringBuilder();
        for(String cookie : newCookies)
            builder.append(";").append(cookie.trim());
        builder.deleteCharAt(0);
        return builder.toString();
    }

    private static ArrayList<String> removeOwnCookieNameFromCookieHeader(String ownCookieName, String[] cookies) {
        ArrayList<String> newCookies = new ArrayList<String>();
        for(String cookie : cookies)
            if(!cookie.trim().startsWith(ownCookieName))
                newCookies.add(cookie.trim());
        return newCookies;
    }

    private static String[] getCookieHeaderSplit(Exchange exc) {
        try {
            return exc.getRequest().getHeader().getFirstValue("Cookie").split(Pattern.quote(";"));
        }catch(NullPointerException e){
            return null;
        }
    }


}
