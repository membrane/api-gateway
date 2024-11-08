package com.predic8.membrane.core.oauth2;

import io.restassured.response.Response;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpHeaders.LOCATION;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.hamcrest.text.MatchesPattern.matchesPattern;

public class OAuth2AuthFlowClient {

    private static final String CLIENT_BASE_URL = "http://localhost:2000";
    private static final String CLIENT_URL = CLIENT_BASE_URL + "/a?b=c&d= ";
    private static final String AUTH_SERVER_URL = "http://localhost:2002";

    static Map<String, String> cookies = new HashMap<>();
    static Map<String, String> memCookies = new HashMap<>();

    // @formatter:off
    static @NotNull Response step1originalRequest() {
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

    static @NotNull String step2sendAuthToOAuth2Server(Response response) {
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

    static void step3openLoginPage(String location) {
        given()
            .redirects().follow(true)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .extract().response();
    }

    static void step4submitLogin(String location) {
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

    static String step5redirectToConsent() {
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

    static void step6openConsentDialog(String location) {
        given()
            .redirects().follow(false)
            .cookies(cookies)
        .when()
            .get(AUTH_SERVER_URL + location)
        .then()
            .statusCode(200)
            .extract().response();
    }

    static void step7submitConsent(String location) {
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

    static String step8redirectToClient() {
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

    static void step9exchangeCodeForToken(String location) {
        given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .post(location)
        .then()
            .statusCode(200)
            .extract().response();
    }
    // @formatter:on
}
