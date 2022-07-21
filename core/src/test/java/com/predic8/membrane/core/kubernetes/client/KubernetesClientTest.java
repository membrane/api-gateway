package com.predic8.membrane.core.kubernetes.client;

import com.google.common.io.Resources;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static com.google.common.io.Resources.getResource;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.interceptor.apimanagement.ApiManagementInterceptor.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class KubernetesClientTest {

    private HttpRouter router;

    @Before
    public void prepare() {
        router = new HttpRouter();
        router.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3053), null, 0);
        sp.getInterceptors().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) throws Exception {
                if ("/openapi/v2".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                            Resources.toString(getResource("kubernetes/api/openapi-v2.json"), UTF_8))
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                if ("/apis".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/apis.json"), UTF_8))
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                if ("/apis/coordination.k8s.io/v1".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/apis-coordination-v1.json"), UTF_8))
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                if ("/api/v1/namespaces/default/secrets/non-existent".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.notFound()
                            .body("{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"secrets \\\"demo\\\" not found\",\"reason\":\"NotFound\",\"details\":{\"name\":\"demo\",\"kind\":\"secrets\"},\"code\":404}")
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                if ("/api/v1/namespaces/default/secrets/existent".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok(
                                    Resources.toString(getResource("kubernetes/api/secret.json"), UTF_8))
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                if ("/version".equals(exc.getRequest().getUri())) {
                    exc.setResponse(Response.ok()
                            .body("{\n" +
                                    "  \"major\": \"1\",\n" +
                                    "  \"minor\": \"23\",\n" +
                                    "  \"gitVersion\": \"v1.23.7\",\n" +
                                    "  \"gitCommit\": \"42c05a547468804b2053ecf60a3bd15560362fc2\",\n" +
                                    "  \"gitTreeState\": \"clean\",\n" +
                                    "  \"buildDate\": \"2022-05-24T12:24:41Z\",\n" +
                                    "  \"goVersion\": \"go1.17.10\",\n" +
                                    "  \"compiler\": \"gc\",\n" +
                                    "  \"platform\": \"linux/amd64\"\n" +
                                    "}")
                            .header(CONTENT_TYPE, APPLICATION_JSON).build());
                }
                return Outcome.RETURN;
            }
        });
        router.getRules().add(sp);
        router.start();
    }

    @After
    public void done() {
        router.stop();
    }

    @Test(expected = KubernetesApiException.class)
    public void read_nonExistent() throws IOException, KubernetesApiException {
        KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

        kc.read("v1", "Secret", "default", "non-existent");
    }

    @Test
    public void read() throws IOException, KubernetesApiException {
        KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

        Map secret = kc.read("v1", "Secret", "default", "existent");

        assertEquals(((Map)secret.get("data")).get("key"), Base64.getEncoder().encodeToString("value".getBytes(UTF_8)));
    }

    @Test
    public void version() throws KubernetesClientBuilder.ParsingException, HttpException, IOException {
        KubernetesClient kc = KubernetesClientBuilder.newBuilder().baseURL("http://localhost:3053/").build();

        assertEquals("v1.23.7", kc.version().get("gitVersion"));
    }

}
