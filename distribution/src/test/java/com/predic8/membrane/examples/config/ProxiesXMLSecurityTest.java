/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.config;

import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;

import static java.lang.Boolean.*;
import static java.nio.file.StandardOpenOption.*;
import static javax.net.ssl.TrustManagerFactory.*;
import static org.junit.jupiter.api.Assertions.*;

public class ProxiesXMLSecurityTest extends AbstractSampleMembraneStartStopTestcase {

    static final String DISABLE_HOSTNAME_VERIFICATION = "jdk.internal.httpclient.disableHostnameVerification";

    @Override
    protected String getExampleDirName() {
        return "..";
    }

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {

        System.getProperties().setProperty(DISABLE_HOSTNAME_VERIFICATION, TRUE.toString());

        process = new Process2.Builder().in(baseDir).script("service-proxy").parameters("-c conf/proxies-security.xml").waitForMembrane().start();
    }

    @Test
    public void webConsole() throws Exception {

        HttpRequest req = HttpRequest.newBuilder().uri(new URI("https://localhost:9000/admin")).build();

        HttpResponse<String> response = getHttpClient().send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ServiceProxies"));
    }

    private HttpClient getHttpClient() throws NoSuchAlgorithmException, KeyManagementException, IOException, KeyStoreException, CertificateException {
        return HttpClient.newBuilder().sslContext(getSslContext()).authenticator(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("admin", "membrane".toCharArray());
            }
        }).build();
    }

    private SSLContext getSslContext() throws NoSuchAlgorithmException, KeyManagementException, IOException, KeyStoreException, CertificateException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
                null,
                getTrustManagerFactory().getTrustManagers(),
                null
        );
        return sslContext;
    }

    private TrustManagerFactory getTrustManagerFactory() throws IOException, NoSuchAlgorithmException, KeyStoreException, CertificateException {
        InputStream trustStoreStream = Files.newInputStream(Paths.get(new File(baseDir, "conf/client.jks").getAbsolutePath()), READ);
        TrustManagerFactory trustManagerFactory = getTrustManagerFactory(getTrustStore(trustStoreStream));
        trustStoreStream.close();
        return trustManagerFactory;
    }

    private KeyStore getTrustStore(InputStream trustStoreStream) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(trustStoreStream, "secret".toCharArray());
        return trustStore;
    }

    private TrustManagerFactory getTrustManagerFactory(KeyStore trustStore) throws NoSuchAlgorithmException, KeyStoreException {
        TrustManagerFactory trustManagerFactory = getInstance(getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory;
    }
}
