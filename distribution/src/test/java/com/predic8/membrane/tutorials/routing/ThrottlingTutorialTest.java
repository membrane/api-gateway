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

package com.predic8.membrane.tutorials.routing;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThrottlingTutorialTest extends AbstractRoutingTutorialTest {

    @Override
    protected String getTutorialYaml() {
        return "70-Throttling.yaml";
    }

    @Test
    void delayedApiAnswersAfterOneSecond() {
        // @formatter:off
        given()
        .when()
            .get("http://localhost:2000/delayed")
        .then()
            .statusCode(200)
            .body(equalTo("Answered after a delay"))
            .time(greaterThanOrEqualTo(1000L));
        // @formatter:on
    }

    @Test
    void limitedApiRejectsThirdConcurrentRequest() {
        try (final var client = HttpClient.newBuilder().version(HTTP_1_1).build()) {
            final var request = HttpRequest.newBuilder(URI.create("http://localhost:2000/limited")).build();
            final var statusCodes = IntStream.range(0, 3)
                    .mapToObj(i -> client.sendAsync(request, HttpResponse.BodyHandlers.discarding()))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .map(HttpResponse::statusCode)
                    .sorted()
                    .toList();
            assertEquals(List.of(200, 200, 503), statusCodes);
        }
    }
}
