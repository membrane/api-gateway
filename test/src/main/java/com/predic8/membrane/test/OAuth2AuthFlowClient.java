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
package com.predic8.membrane.test;

import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.UrlDecoder;
import io.restassured.http.Cookie;
import io.restassured.http.Cookies;
import io.restassured.response.Response;
import org.hamcrest.Matchers;
import org.hamcrest.text.MatchesPattern;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.LOCATION;

public class OAuth2AuthFlowClient {

    private final URI authServerBaseUrl;
    private final URI clientBaseUrl;

    Map<String, String> cookies = new HashMap<>();
    Map<String, String> memCookies = new HashMap<>();

    public OAuth2AuthFlowClient(URI authServerBaseUrl, URI clientBaseUrl) {
        this.authServerBaseUrl = authServerBaseUrl;
        this.clientBaseUrl = clientBaseUrl;
    }

    // @formatter:off
    @NotNull public Response step1originalRequestGET(String url) {
        Response response =
                given()
                    .redirects().follow(false)
                .when()
                    .get(clientBaseUrl.resolve(url).toString())
                .then()
                    .statusCode(307)
                    .header(LOCATION, MatchesPattern.matchesPattern(authServerBaseUrl + ".*"))
                    .extract().response();
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response;
    }

    @NotNull public Response step1originalRequestPOST(String url) {
        Response response =
                given()
                    .redirects().follow(false)
                    .headers(CONTENT_TYPE, "text/x-json")
                    .body("[true]")
                .when()
                    .post(clientBaseUrl.resolve(url).toString())
                .then()
                    .statusCode(307)
                    .header(LOCATION, MatchesPattern.matchesPattern(authServerBaseUrl + ".*"))
                    .extract().response();
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response;
    }

    @NotNull public String step2sendAuthToOAuth2Server(Response response) {
        Response formRedirect =
                given()
                    .redirects().follow(false)
                    .cookies(cookies)
                    .urlEncodingEnabled(false)
                .when()
                    .get(response.getHeader(LOCATION))
                .then()
                    .statusCode(307)
                    .header(LOCATION, MatchesPattern.matchesPattern("/login.*"))
                    .extract().response();
        doUserAgentCookieHandling(cookies, formRedirect.getDetailedCookies());
        return formRedirect.getHeader(LOCATION);
    }

    public void step3openLoginPage(String location) {
        Response login = given()
            .redirects().follow(true)
            .cookies(cookies)
        .when()
            .get(authServerBaseUrl.resolve(location).toString())
        .then()
            .statusCode(200)
            .extract().response();
        doUserAgentCookieHandling(cookies, login.getDetailedCookies());
    }

    public void step4submitLogin(String location, String username, String password) {
        Response login = given()
            .redirects().follow(false)
            .cookies(cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept-Charset", "UTF-8")
            .formParam("username", username)
            .formParam("password", password)
        .when()
            .post(authServerBaseUrl.resolve(location).toString())
        .then()
            .statusCode(200)
            .header(LOCATION, "/")
            .extract().response();
        doUserAgentCookieHandling(cookies, login.getDetailedCookies());
    }

    public String step5redirectToConsent() {
        var response = given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .get(authServerBaseUrl.toString())
            .then()
                .statusCode(307)
                .header(LOCATION, MatchesPattern.matchesPattern("/login/consent.*"))
                .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
        return response.getHeader(LOCATION);
    }

    public void step6openConsentDialog(String location) {
        var response = given()
            .redirects().follow(false)
            .cookies(cookies)
        .when()
            .get(authServerBaseUrl.resolve(location).toString())
        .then()
            .statusCode(200)
            .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
    }

    public void step7submitConsent(String location) {
        var response = given()
            .redirects().follow(false)
            .cookies(cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept-Charset", "UTF-8")
            .formParam("consent", "Accept")
        .when()
            .post(authServerBaseUrl.resolve(location).toString())
        .then()
            .statusCode(200)
            .header(LOCATION, "/")
            .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
    }

    public String step8redirectToClient() {
        var response = given()
                .redirects().follow(false)
                .cookies(cookies)
            .when()
                .post(authServerBaseUrl.toString())
            .then()
                .statusCode(307)
                .header(LOCATION, MatchesPattern.matchesPattern(clientBaseUrl.toString() + ".*"))
                .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
        return response.getHeader(LOCATION);
    }

    public void step9exchangeCodeForToken(String location, String expectedBody) {
        Response response = given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .post(location)
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(307)
            .extract().response();

        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        String location2 = response.getHeader(LOCATION);

        // this is what browsers seem to do
        location2 = UrlDecoder.urlDecode(clientBaseUrl.resolve(location2).toString(), UTF_8, true);

        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .get(location2)
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(200)
            .assertThat().body(Matchers.is(expectedBody));
    }
    // @formatter:on

    /**
     * Please note that the cookie handling on the User Agent side implemented only works exactly for this test.
     */
    private void doUserAgentCookieHandling(Map<String, String> memCookies, Cookies cookies) {
        cookies.asList().stream()
                .filter(e -> e.hasExpiryDate() && e.getExpiryDate().before(new Date()))
                .forEach(c -> memCookies.remove(c.getName()));
        memCookies.putAll(cookies.asList().stream()
                .filter(e -> !e.hasExpiryDate() || !e.getExpiryDate().before(new Date()))
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue)));
    }

}
