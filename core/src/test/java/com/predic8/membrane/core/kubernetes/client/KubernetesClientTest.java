/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.kubernetes.client;

import com.google.common.io.Resources;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class KubernetesClientTest {

    private static HttpRouter router;

    @BeforeAll
    public static void prepare() {
        router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3053), null, 0);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                try {
                    return handleRequestInternal(exc);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public Outcome handleRequestInternal(Exchange exc) throws IOException {
                if ("/openapi/v2".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                            Resources.toString(getResource("kubernetes/api/openapi-v2.json"), UTF_8))
                                    .contentType(APPLICATION_JSON).build());
                }
                if ("/apis".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/apis.json"), UTF_8))
                                    .contentType(APPLICATION_JSON).build());
                }
                if ("/apis/coordination.k8s.io/v1".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/apis-coordination-v1.json"), UTF_8))
                                    .contentType(APPLICATION_JSON).build());
                }
                if ("/api/v1/namespaces/default/secrets/non-existent".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.notFound()
                            .body("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"secrets \\\"demo\\\" not found\",\"reason\":\"NotFound\",\"details\":{\"name\":\"demo\",\"kind\":\"secrets\"},\"code\":404}")
                                    .contentType(APPLICATION_JSON).build());
                }
                if ("/api/v1/namespaces/default/secrets/existent".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/secret.json"), UTF_8))
                                    .contentType(APPLICATION_JSON).build());
                }
                if ("/version".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok()
                            .body("""
                                    {
                                      "major": "1",
                                      "minor": "23",
                                      "gitVersion": "v1.23.7",
                                      "gitCommit": "42c05a547468804b2053ecf60a3bd15560362fc2",
                                      "gitTreeState": "clean",
                                      "buildDate": "2022-05-24T12:24:41Z",
                                      "goVersion": "go1.17.10",
                                      "compiler": "gc",
                                      "platform": "linux/amd64"
                                    }""")
                                    .contentType(APPLICATION_JSON).build());
                }
                return RETURN;
            }
        });
        router.getRules().add(sp);
        router.start();
    }

    @AfterAll
    public static void done() {
        router.stop();
    }

    @Test
    public void read_nonExistent() {
        assertThrows(KubernetesApiException.class, () -> {
            KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

            kc.read("v1", "Secret", "default", "non-existent");
        });
    }

    @Test
    public void read() throws IOException, KubernetesApiException {
        KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

        Map secret = kc.read("v1", "Secret", "default", "existent");

        assertEquals(((Map<?, ?>)secret.get("data")).get("key"), Base64.getEncoder().encodeToString("value".getBytes(UTF_8)));
    }

    @Test
    public void version() throws HttpException, IOException {
        KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

        assertEquals("v1.23.7", kc.version().get("gitVersion"));
    }
}