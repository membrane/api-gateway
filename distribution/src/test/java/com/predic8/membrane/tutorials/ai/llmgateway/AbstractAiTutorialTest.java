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

package com.predic8.membrane.tutorials.ai.llmgateway;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.examples.util.DistributionExtractingTestcase;
import com.predic8.membrane.examples.util.Process2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Consumer;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;

/**
 * Base class for AI tutorial tests. Starts a local Membrane mock of the upstream LLM API
 * so tests run without a real API key and without network access to the LLM provider.
 *
 * <p>The tutorial YAML's {@code target.url} is rewritten to point at the mock server
 * before Membrane starts. Subclasses override {@link #getTutorialDir()} and
 * {@link #getTutorialYaml()} to select the tutorial under test.
 *
 * <p>JUnit 5 lifecycle ordering guarantees that {@code DistributionExtractingTestcase.init()}
 * (superclass {@code @BeforeEach}) runs first and sets {@code baseDir}, allowing
 * {@link #startGateway()} to use {@code replaceInFile2()} safely.
 */
public abstract class AbstractAiTutorialTest extends DistributionExtractingTestcase {

    protected static final int MOCK_LLM_PORT = 3100;

    /**
     * Value substituted for the {@code <<Replace with your API_KEY>>} placeholder in tutorial
     * YAMLs before Membrane starts. Tests that verify upstream key-substitution assert against
     * this constant instead of the raw placeholder text.
     */
    protected static final String TEST_API_KEY = "test-upstream-key";

    protected Process2 process;
    protected volatile String lastRequestBody;
    protected volatile String lastRequestApiKey;

    private DefaultRouter mockRouter;

    protected abstract String getTutorialDir();
    protected abstract String getTutorialYaml();

    @Override
    protected String getExampleDirName() {
        return "../tutorials/%s".formatted(getTutorialDir());
    }

    @Override
    protected String getParameters() {
        return "-c %s".formatted(getTutorialYaml());
    }

    /**
     * Runs after {@code DistributionExtractingTestcase.init()} sets {@code baseDir}.
     * Starts the mock, patches the YAML, then starts Membrane.
     */
    @BeforeEach
    void startGateway() throws Exception {
        startMockLlmApi();
        replaceInFile2(getTutorialYaml(), getUpstreamApiUrl(), mockApiUrl());
        replaceInFile2(getTutorialYaml(), "<<Replace with your API_KEY>>", TEST_API_KEY);
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopGateway() {
        if (process != null)
            process.killScript();
        if (mockRouter != null)
            mockRouter.stop();
    }

    /**
     * The upstream API URL used in the tutorial YAML (to be replaced by the mock URL).
     */
    protected String getUpstreamApiUrl() {
        return "https://api.anthropic.com";
    }

    protected String mockApiUrl() {
        return "http://localhost:" + MOCK_LLM_PORT;
    }

    /**
     * The HTTP header name from which the upstream API key is read when capturing
     * requests in the mock. Defaults to {@code "x-api-key"} (Claude). Override to
     * {@code "authorization"} for OpenAI or {@code "x-goog-api-key"} for Google.
     */
    protected String getApiKeyHeader() {
        return "x-api-key";
    }

    /**
     * Content-Type the mock LLM server sends back. Defaults to {@code "application/json"}
     * for regular responses. Override to {@code "text/event-stream"} in streaming test classes.
     */
    protected String mockContentType() {
        return APPLICATION_JSON;
    }

    private void startMockLlmApi() throws Exception {
        var si = new StaticInterceptor();
        si.setSrc(mockResponse());
        si.setContentType(mockContentType());

        var sp = new ServiceProxy(new ServiceProxyKey(MOCK_LLM_PORT), null, 0);
        sp.getFlow().add(new BodyCaptureInterceptor(
                body   -> lastRequestBody   = body,
                apiKey -> lastRequestApiKey = apiKey,
                getApiKeyHeader()));
        sp.getFlow().add(si);
        sp.getFlow().add(new ReturnInterceptor());

        mockRouter = new DefaultRouter();
        mockRouter.add(sp);
        mockRouter.start();
    }

    private static class BodyCaptureInterceptor extends AbstractInterceptor {

        private final Consumer<String> bodySink;
        private final Consumer<String> apiKeySink;
        private final String apiKeyHeader;

        BodyCaptureInterceptor(Consumer<String> bodySink, Consumer<String> apiKeySink, String apiKeyHeader) {
            this.bodySink     = bodySink;
            this.apiKeySink   = apiKeySink;
            this.apiKeyHeader = apiKeyHeader;
        }

        @Override
        public Outcome handleRequest(Exchange exc) {
            bodySink.accept(exc.getRequest().getBodyAsStringDecoded());
            apiKeySink.accept(exc.getRequest().getHeader().getFirstValue(apiKeyHeader));
            return Outcome.CONTINUE;
        }
    }

    protected String mockResponse() {
        return """
                {"id":"msg_mock","type":"message","role":"assistant",\
                "content":[{"type":"text","text":"I am a mock."}],\
                "model":"claude-sonnet-4-0","stop_reason":"end_turn",\
                "usage":{"input_tokens":10,"output_tokens":5}}""";
    }
}
