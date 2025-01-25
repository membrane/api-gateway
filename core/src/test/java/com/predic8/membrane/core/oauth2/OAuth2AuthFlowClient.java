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
package com.predic8.membrane.core.oauth2;

import io.restassured.response.*;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.predic8.membrane.core.resolver.ResolverMap.*;
import static io.restassured.RestAssured.*;
import static io.restassured.filter.log.LogDetail.*;
import static io.restassured.filter.log.UrlDecoder.*;
import static java.nio.charset.StandardCharsets.*;
import static org.apache.http.HttpHeaders.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

public class OAuth2AuthFlowClient {

    private static final String CLIENT_BASE_URL = "http://localhost:2000";
    private static final String CLIENT_URL = CLIENT_BASE_URL + "/a?b=c&d=ä";
    private static final String AUTH_SERVER_URL = "http://localhost:2002";

    Map<String, String> cookies = new HashMap<>();
    Map<String, String> memCookies = new HashMap<>();

    // @formatter:off
    @NotNull Response step1originalRequestGET() {
        Response response =
                given()
                    .redirects().follow(false)
                .when()
                    .get(CLIENT_URL)
                .then()
                    .statusCode(307)
                    .header(LOCATION, matchesPattern(AUTH_SERVER_URL + ".*"))
                    .extract().response();
        memCookies.putAll(response.getCookies());
        return response;
    }

    @NotNull Response step1originalRequestPOST() {
        Response response =
                given()
                    .redirects().follow(false)
                    .headers(CONTENT_TYPE, "text/x-json")
                    .body("[true]")
                .when()
                    .post(CLIENT_URL)
                .then()
                    .statusCode(307)
                    .header(LOCATION, matchesPattern(AUTH_SERVER_URL + ".*"))
                    .extract().response();
        memCookies.putAll(response.getCookies());
        return response;
    }

    @NotNull String step2sendAuthToOAuth2Server(Response response) {
        Response formRedirect =
                given()
                    .redirects().follow(false)
                    .cookies(cookies)
                    .urlEncodingEnabled(false)
                .when()
                    .get(response.getHeader(LOCATION))
                .then()
                    .statusCode(307)
                    .header(LOCATION, matchesPattern("/login.*"))
                    .extract().response();
        cookies.putAll(formRedirect.getCookies());
        return formRedirect.getHeader(LOCATION);
    }

    void step3openLoginPage(String location) {
        given()
            .redirects().follow(true)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .extract().response();
    }

    void step4submitLogin(String location) {
        given()
            .redirects().follow(false)
            .cookies(cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept-Charset", "UTF-8")
            .formParam("username", "user")
            .formParam("password", "password")
        .when()
            .post(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .header(LOCATION, "/")
            .extract().response();
    }

    String step5redirectToConsent() {
        return given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .get(AUTH_SERVER_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern("/login/consent.*"))
                .extract().response().getHeader(LOCATION);
    }

    void step6openConsentDialog(String location) {
        given()
            .redirects().follow(false)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .extract().response();
    }

    void step7submitConsent(String location) {
        given()
            .redirects().follow(false)
            .cookies(cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept-Charset", "UTF-8")
            .formParam("consent", "Accept")
        .when()
            .post(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .header(LOCATION, "/")
            .extract().response();
    }

    String step8redirectToClient() {
        return given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .post(AUTH_SERVER_URL)
            .then()
                .statusCode(307)
                .header(LOCATION, matchesPattern(CLIENT_BASE_URL + ".*"))
                .extract().response().getHeader(LOCATION);
    }

    void step9exchangeCodeForToken(String location, String expectedBody) {
        String location2 = given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .post(location)
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(307)
            .extract().response().getHeader(LOCATION);

        // this is what browsers seem to do
        location2 = urlDecode(combine(location, location2), UTF_8, true);

        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .get(location2)
        .then()
            .log().ifValidationFails(ALL)
            .statusCode(200)
            .assertThat().body(is(expectedBody));
    }
    // @formatter:on
}
