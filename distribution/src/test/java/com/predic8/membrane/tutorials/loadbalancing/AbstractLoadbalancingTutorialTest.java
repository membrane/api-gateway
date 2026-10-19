/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.tutorials.loadbalancing;

import com.predic8.membrane.tutorials.AbstractMembraneTutorialTest;

import java.util.HashSet;
import java.util.Set;

import static io.restassured.RestAssured.given;

public abstract class AbstractLoadbalancingTutorialTest extends AbstractMembraneTutorialTest {

    @Override
    protected String getTutorialDir() {
        return "loadbalancing";
    }

    static String call(String url) {
        // @formatter:off
        return given()
            .when()
                .get(url)
            .then()
                .statusCode(200)
                .extract().asString();
        // @formatter:on
    }

    static Set<String> callTimes(String url, int times) {
        final var answers = new HashSet<String>();
        for (var i = 0; i < times; i++)
            answers.add(call(url));
        return answers;
    }
}
