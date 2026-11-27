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

package com.predic8.membrane.tutorials.rate_limiting.redis;

import com.predic8.membrane.examples.util.Process2;
import com.predic8.membrane.tutorials.AbstractMembraneTutorialTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.restassured.RestAssured.given;

/**
 * Starts both instances of the tutorial as separate processes, with {@link FakeRedisServer} on a free port instead of
 * a real Redis on 6379.
 */
class SharedRateLimitWithRedisTutorialTest extends AbstractMembraneTutorialTest {

    private static final String INSTANCE_B_YAML = "instance-b.yaml";

    private FakeRedisServer redis;
    private Process2 instanceB;

    @Override
    protected String getTutorialDir() {
        return "rate-limiting/redis";
    }

    @Override
    protected String getTutorialYaml() {
        return "instance-a.yaml";
    }

    @Override
    protected void startMembrane() throws Exception {
        redis = new FakeRedisServer();
        replaceInFile2(getTutorialYaml(), "port: 6379", "port: " + redis.getPort());
        replaceInFile2(INSTANCE_B_YAML, "port: 6379", "port: " + redis.getPort());
        super.startMembrane();
        instanceB = new Process2.Builder().in(baseDir).script("membrane").withParameters("-c " + INSTANCE_B_YAML)
                .withEnv(getEnvs()).waitForMembrane().start();
    }

    @Override
    protected void stopMembrane() {
        if (instanceB != null) {
            instanceB.killScript();
            instanceB = null;
        }
        super.stopMembrane();
        try {
            if (redis != null)
                redis.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void instancesShareTheLimit() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("http://localhost:2000")
        .then()
            .statusCode(200)
            .header("X-RateLimit-Limit", "3")
            .header("X-RateLimit-Remaining", "1");

        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("http://localhost:2001")
        .then()
            .statusCode(429)
            .header("X-RateLimit-Remaining", "0");
        // @formatter:on
    }
}
