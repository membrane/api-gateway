/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util;

import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AssertUtils {

    private static CloseableHttpClient hc = HttpClientBuilder.create()
            .setDefaultCookieSpecRegistry(l -> {
                return new RFC6265CookieSpecProvider();
            }).build();

    public static void assertContains(String needle, String haystack) {
        if (haystack.contains(needle))
            return;
        throw new AssertionError("The string '" + haystack + "' does not contain '" + needle + "'.");
    }

    public static void assertContainsNot(String needle, String haystack) {
        if (!haystack.contains(needle))
            return;
        throw new AssertionError("The string '" + haystack + "' does contain '" + needle + "'.");
    }

    public static String getAndAssert200(String url) throws ParseException, IOException {
        return getAndAssert(200, url);
    }

    public static String getAndAssert200(String url, String[] header) throws ParseException, IOException {
        return getAndAssert(200, url, header);
    }

    public static String getAndAssert(int expectedHttpStatusCode, String url) throws ParseException, IOException {
        return getAndAssert(expectedHttpStatusCode, url, null);
    }

    public static String getAndAssert(int expectedHttpStatusCode, String url, String[] header) throws ParseException, IOException {
        HttpGet get = new HttpGet(url);

        if (header != null)
            for (int i = 0; i < header.length; i += 2)
                get.addHeader(header[i], header[i + 1]);

        try (CloseableHttpResponse res1 = hc.execute(get)) {
            try {
                assertEquals(expectedHttpStatusCode, res1.getStatusLine().getStatusCode());
            } catch (AssertionError e) {
                throw new AssertionError(e.getMessage() + " while fetching " + url);
            }
            HttpEntity entity = res1.getEntity();
            return entity == null ? "" : EntityUtils.toString(entity);
        }
    }
}
