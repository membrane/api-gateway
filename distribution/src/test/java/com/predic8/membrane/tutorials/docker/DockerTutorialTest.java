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

package com.predic8.membrane.tutorials.docker;

import com.predic8.membrane.examples.util.BufferLogger;
import com.predic8.membrane.examples.util.SubstringWaitableConsoleEvent;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.OSUtil.isWindows;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verifies conf/apis.yaml of the Docker tutorial without Docker: without BACKEND_URL the
 * Gateway API forwards to the mock. Running the container, the image build and the
 * environment variables of step 40 are not covered.
 */
public class DockerTutorialTest extends AbstractDockerTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "conf/apis.yaml";
    }

    /**
     * tutorials/docker has no launcher of its own, because its readers use Docker. Starts the one
     * in the root of the distribution instead. The working directory stays tutorials/docker, so
     * the relative conf/apis.yaml is resolved against it.
     */
    @Override
    protected void startMembrane() throws Exception {
        logger = new BufferLogger();
        process = startServiceProxyScript(logger, isWindows() ? "..\\..\\membrane" : "../../membrane");
    }

    @Test
    void forwardsToMockByDefault() throws Exception {
        final var logged = new SubstringWaitableConsoleEvent(process, "Got: /");

        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .body(equalTo("{ \"backend\": \"mock\" }"));
        // @formatter:on

        logged.waitFor(5000);
    }
}
