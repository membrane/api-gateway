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

package com.predic8.membrane.examples.withoutinternet.oauth2;

import com.predic8.membrane.examples.util.*;
import com.predic8.membrane.test.OAuth2AuthFlowClient;
import io.restassured.*;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.response.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.URI;

import static io.restassured.RestAssured.*;

public class OAuth2Membrane2ExampleTest extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "security/oauth2/membrane";
    }

    Process2 authorizationServer;
    Process2 oauth2Client;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        authorizationServer = new Process2.Builder().in(getExampleDir( getExampleDirName() + "/authorization_server")).script("membrane").waitForMembrane().start();
        oauth2Client = new Process2.Builder().in(getExampleDir(getExampleDirName() + "/client")).script("membrane").waitForMembrane().start();

        // Dump HTTP
        RestAssured.filters(new RequestLoggingFilter(), new ResponseLoggingFilter());
    }

    @AfterEach
    void stopMembrane() {
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
        OAuth2AuthFlowClient OAuth2 = new OAuth2AuthFlowClient(URI.create("http://localhost:8000"), URI.create("http://localhost:2000"));
        // Step 1: Initial request to the client
        Response clientResponse = OAuth2.step1originalRequestGET("/");
        // Step 2: Send to authentication at OAuth2 server
        String loginLocation = OAuth2.step2sendAuthToOAuth2Server(clientResponse);
        System.out.println("loginLocation = " + loginLocation);
        // Step 3: Open login page
        OAuth2.step3openLoginPage(loginLocation);
        // Step 4: Submit login
        OAuth2.step4submitLogin(loginLocation, "john", "password");
        // Step 5: Redirect to consent
        String consentLocation = OAuth2.step5redirectToConsent();
        // Step 6: Open consent dialog
        OAuth2.step6openConsentDialog(consentLocation);
        // Step 7: Submit consent
        OAuth2.step7submitConsent(consentLocation);
        // Step 8: Redirect back to client
        String callbackUrl = OAuth2.step8redirectToClient();
        // Step 9: Exchange Code for Token & continue original request.·
        OAuth2.step9exchangeCodeForToken(
                callbackUrl,
                "You accessed the protected resource! Hello john@predic8.de"
        );
    }
}
