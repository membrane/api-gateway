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

package com.predic8.membrane.core.interceptor.prometheus;

import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.validators.ValidationContext;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.annot.Constants.VERSION;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.openapi.validators.ValidationErrors.error;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.MANUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusInterceptorTest {

    @Test
    void infoMetricContainsUnmodifiedVersion() throws Exception {
        final var router = new TestRouter();
        router.init();
        final var interceptor = new PrometheusInterceptor();
        interceptor.init(router);

        final var exc = get("/metrics").buildExchange();
        interceptor.handleRequest(exc);

        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("membrane_info{version=\"%s\"} 1\n".formatted(VERSION)), body);
    }

    @Test
    void validationLabelValuesAreEscaped() throws Exception {
        final var router = new TestRouter();
        router.init();
        final var api = new APIProxy();
        api.setName("api");
        api.getValidationStatisticCollector().collect(error(new ValidationContext()
                .method("GET")
                .path("/a\"b\\c\nd")
                .entity("e\"f"), "invalid"));
        router.getRuleManager().addProxy(api, MANUAL);
        final var interceptor = new PrometheusInterceptor();
        interceptor.init(router);

        final var exc = get("/metrics").buildExchange();
        interceptor.handleRequest(exc);

        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("path=\"/a\\\"b\\\\c\\nd\""), body);
        assertTrue(body.contains("entity=\"e\\\"f\""), body);
        // Every line is a comment or a complete sample: an unescaped newline would split a sample.
        body.lines().forEach(line -> assertTrue(line.startsWith("#") || line.matches(".*[}\\w] -?\\d+(\\.\\d+)?$"), line));
    }

    @Test
    void jvmMetricsOnlyWhenEnabled() throws Exception {
        assertFalse(scrape(false).lines().anyMatch(line -> line.startsWith("jvm_") || line.startsWith("process_") || line.startsWith("# TYPE jvm_") || line.startsWith("# TYPE process_")));

        final var body = scrape(true);
        assertTrue(body.contains("membrane_info{version="), body);
        assertTrue(body.contains("# TYPE jvm_memory_used_bytes gauge\n"), body);
    }

    private static String scrape(boolean jvm) throws Exception {
        final var router = new TestRouter();
        router.init();
        final var interceptor = new PrometheusInterceptor();
        interceptor.setJvm(jvm);
        interceptor.init(router);

        final var exc = get("/metrics").buildExchange();
        interceptor.handleRequest(exc);
        return exc.getResponse().getBodyAsStringDecoded();
    }

    @Test
    void escapeLabelValue() {
        assertEquals("plain 7.7.1", PrometheusInterceptor.escapeLabelValue("plain 7.7.1"));
        assertEquals("a\\\\b\\\"c\\nd", PrometheusInterceptor.escapeLabelValue("a\\b\"c\nd"));
    }
}
