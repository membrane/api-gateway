/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.oauth2;

import com.predic8.membrane.examples.util.*;
import io.restassured.*;
import io.restassured.filter.log.*;
import io.restassured.response.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class OAuth2MembraneTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "oauth2/membrane";
    }

    Process2 authorizationServer;
    Process2 oauth2Client;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        authorizationServer = new Process2.Builder().in(getExampleDir("oauth2/membrane/authorization_server")).script("service-proxy").waitForMembrane().start();
        oauth2Client = new Process2.Builder().in(getExampleDir("oauth2/membrane/client")).script("service-proxy").waitForMembrane().start();

        // Dump HTTP
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        oauth2Client.killScript();
        authorizationServer.killScript();
    }

    @Test
    void testWellKnown() {
        // @formatter:off
        given()
            .get("http://localhost:8000/.well-known/openid-configuration")
        .then().assertThat()
            .body("issuer", CoreMatchers.equalTo("http://localhost:8000"))
            .body("authorization_endpoint", CoreMatchers.equalTo("http://localhost:8000/oauth2/auth"))
            .body("token_endpoint", CoreMatchers.equalTo("http://localhost:8000/oauth2/token"))
            .body("userinfo_endpoint", CoreMatchers.equalTo("http://localhost:8000/oauth2/userinfo"));
        // @formatter:on
    }

    @Test
    void loginPage() {
        Map<String, String> cookies = new HashMap<>();
        callOAuth2Auth(cookies, tryToAccessSite(cookies));
        requestLoginPage(cookies);
        postLogin(cookies);
        sendConsent(cookies);
        accessTargetResource(cookies);
    }

    private static void accessTargetResource(Map<String, String> cookies) {
        // @formatter:off
        given()
            .cookies(cookies)
            .get("http://localhost:8000")
        .then().assertThat()
            .statusCode(200)
            .body(containsString("accessed"))
            .body(containsString("john@predic8.de"));
        // @formatter:on
    }

    private static void requestLoginPage(Map<String, String> cookies) {
        // @formatter:off
        given().cookies(cookies).get("http://localhost:8000/login/").then().assertThat()
                .statusCode(200)
                .contentType(TEXT_HTML);

        // @formatter:on
    }

    private static void sendConsent(Map<String, String> cookies) {
        // @formatter:off
        given()
                .cookies(cookies)
                .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
                .formParam("target","")
                .formParam("consent","Accept")
                .redirects().follow(false)
                .post("http://localhost:8000/login/consent")
        .then().assertThat()
                .statusCode(200)
                .header("Location",equalTo("/"))
                .body(containsString("http-equiv=\"refresh\""));
        // @formatter:on
    }

    private static void postLogin(Map<String, String> cookies) {
        // @formatter:off
        given()
            .cookies(cookies)
            .contentType(APPLICATION_X_WWW_FORM_URLENCODED)
            .formParam("target","")
            .formParam("username","john")
            .formParam("password","password")
        .post("http://localhost:8000/login/")
        .then().assertThat()
            .statusCode(200)
            .contentType(TEXT_HTML)
            .header("Location","/");
        // @formatter:on
    }

    private static void callOAuth2Auth(Map<String, String> cookies, String authUrl) {
        // @formatter:off
        Response loginPage = given().redirects().follow(false)
                .cookies(cookies)
                .urlEncodingEnabled(false)
                .get(authUrl);

        loginPage.then().assertThat()
                .statusCode(307)
                .header("Location",equalTo("/login/"))
                .contentType(TEXT_HTML);
        // @formatter:on

        cookies.putAll(loginPage.cookies());
    }

    private static String tryToAccessSite(Map<String, String> cookies) {
        // @formatter:off
       Response response = given()
               .redirects().follow(false)
               .get("http://localhost:2000/");

       response.then().assertThat()
               .statusCode(307)
               .header("Location", startsWith("http://localhost:8000/oauth2/auth"));
        // @formatter:on

        cookies.putAll(response.cookies());
        return response.getHeader("Location");
    }
}
