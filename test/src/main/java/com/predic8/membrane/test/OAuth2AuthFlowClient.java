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
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.*;

public abstract class OAuth2AuthFlowClient {


    protected final URI authServerBaseUrl;
    protected final URI clientBaseUrl;

    Map<String, String> cookies = new HashMap<>();
    Map<String, String> memCookies = new HashMap<>();

    public OAuth2AuthFlowClient(URI authServerBaseUrl, URI clientBaseUrl) {
        this.authServerBaseUrl = authServerBaseUrl;
        this.clientBaseUrl = clientBaseUrl;
    }

    // @formatter:off
    @NotNull public Response step1originalRequestGET(String url) {
        Response response = given()
            .redirects().follow(false)
        .when()
            .get(clientBaseUrl.resolve(url).toString())
        .then()
            .statusCode(302)
            .header(LOCATION, MatchesPattern.matchesPattern(authServerBaseUrl.toString() + ".*"))
            .extract().response();
        checkStep1Response(response);
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response;
    }

    @NotNull public Response step1originalRequestPOST(String url) {
        Response response = given()
            .redirects().follow(false)
            .headers(CONTENT_TYPE, "text/x-json")
            .body("[true]")
        .when()
            .post(clientBaseUrl.resolve(url).toString())
        .then()
            .statusCode(302)
            .header(LOCATION, MatchesPattern.matchesPattern(authServerBaseUrl.toString() + ".*"))
            .extract().response();
        checkStep1Response(response);
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response;
    }

    abstract void checkStep1Response(Response response);

    @NotNull public String step2sendAuthToOAuth2Server(Response response) {
        Response formRedirect = given()
            .redirects().follow(false)
            .cookies(cookies)
            .urlEncodingEnabled(false)
        .when()
            .get(response.getHeader(LOCATION))
        .then()
            .statusCode(302)
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
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .header(ACCEPT_CHARSET, "UTF-8")
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
                .statusCode(302)
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
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .header(ACCEPT_CHARSET, "UTF-8")
            .formParam("consent", "Accept")
        .when()
            .post(authServerBaseUrl.resolve(location).toString())
        .then()
            .statusCode(200)
            .header(LOCATION, "/")
            .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
    }

    public abstract Response step8redirectToClient()throws URISyntaxException;

    public abstract String step9executeCallback(Response callback);

    public void step10callOriginalUrl(String redirectUri, String expectedBody) {
        var location = UrlDecoder.urlDecode(clientBaseUrl.resolve(redirectUri).toString(), UTF_8, true);
        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .get(location)
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(200)
            .assertThat().body(Matchers.is(expectedBody));

    }
    // @formatter:on

    /**
     * Please note that the cookie handling on the User Agent side implemented only works exactly for this test.
     */
    void doUserAgentCookieHandling(Map<String, String> memCookies, Cookies cookies) {
        cookies.asList().stream()
                .filter(e -> e.hasExpiryDate() && e.getExpiryDate().before(new Date()))
                .forEach(c -> memCookies.remove(c.getName()));
        memCookies.putAll(cookies.asList().stream()
                .filter(e -> !e.hasExpiryDate() || !e.getExpiryDate().before(new Date()))
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue)));
    }

    static Map<String, String> readQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(OAuth2AuthFlowClient::splitQueryElement)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static Map.Entry<String, String> splitQueryElement(String keyValue) {
        var pos = keyValue.indexOf('=');
        return Map.entry(keyValue.substring(0, pos), keyValue.substring(pos + 1));
    }

}
