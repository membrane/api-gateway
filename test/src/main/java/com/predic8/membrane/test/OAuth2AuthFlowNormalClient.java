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
import io.restassured.response.Response;
import org.hamcrest.text.MatchesPattern;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.apache.http.HttpHeaders.LOCATION;

public class OAuth2AuthFlowNormalClient extends OAuth2AuthFlowClient {

    public OAuth2AuthFlowNormalClient(URI authServerBaseUrl, URI clientBaseUrl) {
        super(authServerBaseUrl, clientBaseUrl);
    }

    void checkStep1Response(Response response) {
        var params = readQuery(URI.create(response.getHeader(LOCATION)).getQuery());
        assert !params.containsKey("response_mode") || !params.get("response_mode").equals("form_post");
    }

    // @formatter:off
    public Response step8redirectToClient() {
        var response = given()
            .redirects().follow(false)
            .cookies(cookies)
        .when()
            .get(authServerBaseUrl.toString())
        .then()
            .statusCode(302)
            .header(LOCATION, MatchesPattern.matchesPattern(clientBaseUrl.toString() + ".*"))
            .extract().response();
        doUserAgentCookieHandling(cookies, response.getDetailedCookies());
        return response;
    }

    public String step9executeCallback(Response callback) {
        Response response = given()
            .redirects().follow(false)
            .cookies(memCookies)
        .when()
            .get(callback.getHeader(LOCATION))
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(302)
            .extract().response();
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response.getHeader(LOCATION);
    }
    // @formatter:on
}
