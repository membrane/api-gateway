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

package com.predic8.membrane.examples.withoutinternet;

import com.predic8.membrane.examples.util.AbstractSampleMembraneStartStopTestcase;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

public class JwtVerificationExampleTest extends AbstractSampleMembraneStartStopTestcase {

    @Override
    protected String getExampleDirName() {
        return "/security/jwt/verification";
    }

    @Test
    void request1() {
        // @formatter:off
        given()
        .when()
            .header("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Im1lbWJyYW5lIn0.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiYXVkIjoib3JkZXIiLCJzY29wZSI6ImV4ZWN1dGUiLCJpYXQiOjE3Njc4MDMwODYsImV4cCI6MjA3NTM4NzA4NiwibmJmIjoxNzY3ODAyOTY2fQ.V661gIICLKzg3cAZIhl1632X-H_jQZSfuUjbqqxfShSpJZSBwqMvRLGHTso107miBnnLweNYTMxOjBoPrC5QruDcXbz32RzdGD9rY6uy47ewDCw6W4fzsETjjxstqebYQJIKiR1mGKOi418kv5Vecw23TvtrezRf8bq1ElzljcKzOioYnrxq4fyT8V9uCFccqub5WqMt8DaddfJGMt_WTLmFu_MlU4UMDdghF8wFgFaqGg-J5gQRd-EevfNc1DIw1s-Pu4nEjAgpZ94a1dMX7IbmcrONLrbtjJzoouMeKc_hJyeW8XWsggmULbOOI5sz2T-PL-MgmtQMk8Ewx37QWQ")
            .get("http://localhost:2001/")
        .then()
            .assertThat()
            .statusCode(200);
        // @formatter:on
    }
}
