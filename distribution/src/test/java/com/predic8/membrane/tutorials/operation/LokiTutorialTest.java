/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Response.ok;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies tutorial step 70-Record-Messages-In-Loki.yaml: lokiExchangeStore pushes every finished exchange to
 * <code>/loki/api/v1/push</code>, labelled with <code>job</code> and the name of the API.
 *
 * <p>A local Membrane router stands in for Loki on the port the tutorial configures, so the
 * lesson is verified end-to-end without a Loki container. What the tutorial's LogQL steps
 * promise — a <code>job="membrane-instance-1"</code> stream per API, and lines whose JSON carries method,
 * URI and status code — is exactly what is asserted here.
 */
public class LokiTutorialTest extends AbstractOperationTutorialTest {

    private static final int LOKI_MOCK_PORT = 3100;
    private static final ObjectMapper om = new ObjectMapper();

    private final List<JsonNode> pushes = new ArrayList<>();

    private DefaultRouter lokiMock;

    @Override
    protected String getTutorialYaml() {
        return "70-Record-Messages-In-Loki.yaml";
    }

    /**
     * Hides {@code AbstractSampleMembraneStartStopTestcase.startMembrane()} so the Loki stand-in
     * is listening before Membrane starts and no push can be missed.
     */
    @BeforeEach
    void startMembrane() throws Exception {
        startLokiMock();
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopLokiMock() {
        if (lokiMock != null)
            lokiMock.stop();
    }

    @Test
    void exchangesArePushedToLokiLabelledByJobAndApi() throws Exception {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("http://localhost:2002")
        .then()
            .statusCode(404);
        // @formatter:on

        assertEquals("GET / -> 200", waitForLogLine("Backend A"));
        assertEquals("GET / -> 404", waitForLogLine("Backend B"));
    }

    /**
     * Waits for a log line pushed for the given API and renders it the way the tutorial's
     * {@code line_format "{{.request_method}} {{.request_uri}} -> {{.response_statusCode}}"}
     * would.
     */
    private String waitForLogLine(String api) throws Exception {
        // The store batches and pushes every updateIntervalMs (1000 by default).
        for (int i = 0; i < 100; i++) {
            JsonNode line = findLogLine(api);
            if (line != null)
                return "%s %s -> %d".formatted(
                        line.get("request").get("method").textValue(),
                        line.get("request").get("uri").textValue(),
                        line.get("response").get("statusCode").intValue());
            Thread.sleep(100);
        }
        throw new AssertionError("No exchange pushed to Loki for API '%s' within 10s. Pushes seen: %s"
                .formatted(api, pushes));
    }

    private JsonNode findLogLine(String api) throws Exception {
        synchronized (pushes) {
            for (JsonNode push : pushes)
                for (JsonNode stream : push.get("streams")) {
                    JsonNode labels = stream.get("stream");
                    if (!"membrane-instance-1".equals(labels.get("job").textValue()))
                        continue;
                    if (!api.equals(labels.get("api").textValue()))
                        continue;
                    JsonNode values = stream.get("values");
                    if (!values.isEmpty())
                        // values[i] is [ "<epoch nanos>", "<exchange as JSON>" ]
                        return om.readTree(values.get(0).get(1).textValue());
                }
        }
        return null;
    }

    private void startLokiMock() throws Exception {
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(LOKI_MOCK_PORT), null, 0);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().getUri().equals("/loki/api/v1/push")) {
                    try {
                        synchronized (pushes) {
                            pushes.add(om.readTree(exc.getRequest().getBodyAsStringDecoded()));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
                exc.setResponse(ok().build());
                return Outcome.RETURN;
            }
        });

        lokiMock = new DefaultRouter();
        lokiMock.add(sp);
        lokiMock.start();
    }
}
