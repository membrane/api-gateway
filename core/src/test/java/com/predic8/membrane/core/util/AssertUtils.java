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

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.http.client.protocol.HttpClientContext.*;
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
