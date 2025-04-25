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

import io.restassured.builder.ResponseBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import org.hamcrest.text.MatchesPattern;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeXml;
import static org.apache.http.HttpHeaders.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OAuth2AuthFlowFormPostClient extends OAuth2AuthFlowClient {

    public static final Pattern INPUT_PATTERN = Pattern.compile("<input type=\"hidden\" name=\"(.*?)\" value=\"(.*?)\"\\s*/>", Pattern.CASE_INSENSITIVE);
    public static final Pattern FORM_PATTERN = Pattern.compile("<form method=\"post\" action=\"(.*?)\"", Pattern.CASE_INSENSITIVE);

    public OAuth2AuthFlowFormPostClient(URI authServerBaseUrl, URI clientBaseUrl) {
        super(authServerBaseUrl, clientBaseUrl);
    }

    void checkStep1Response(Response response) {
        var params = readQuery(URI.create(response.getHeader(LOCATION)).getQuery());
        assert params.containsKey("response_mode");
        assertEquals("form_post", params.get("response_mode"));
    }

    // @formatter:off
    public Response step8redirectToClient() throws URISyntaxException {
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
        return rewriteRedirectResponseToForm(response);
    }

    public String step9executeCallback(Response callback) {
        Response response = given()
            .redirects().follow(false)
            .header(CONTENT_TYPE, "application/x-www-form-urlencoded")
            .formParams(gatherInputFields(callback.getBody().asString()))
            .cookies(memCookies)
        .when()
            .post(extractTargetUrl(callback.getBody().asString()))
        .then()
            .log().ifValidationFails(LogDetail.ALL)
            .statusCode(302)
            .extract().response();
        doUserAgentCookieHandling(memCookies, response.getDetailedCookies());
        return response.getHeader(LOCATION);
    }
    // @formatter:on

    private Response rewriteRedirectResponseToForm(Response response) throws URISyntaxException {
        URI location = URI.create(response.getHeader(LOCATION));
        var params = readQuery(location.getQuery());
        var target = new URI(location.getScheme(), location.getAuthority(), location.getPath(), null, location.getFragment()).toString();
        var filteredHeaders = response.getHeaders().asList().stream()
                .filter(h -> !Set.of(LOCATION, CONTENT_LENGTH, CONTENT_TYPE, CACHE_CONTROL, PRAGMA).contains(h.getName()))
                .collect(Collectors.toList());
        filteredHeaders.add(new Header(CONTENT_TYPE, "text/html;charset=UTF-8"));
        filteredHeaders.add(new Header(CACHE_CONTROL, "no-cache, no-store"));
        filteredHeaders.add(new Header(PRAGMA, "no-cache"));
        return new ResponseBuilder()
                .clone(response)
                .setStatusCode(200)
                .setStatusLine("HTTP/1.1 200 OK")
                .setHeaders(new Headers(filteredHeaders))
                .setBody(getFormBody(target, params))
                .build();
    }

    private String getFormBody(String target, Map<String, String> queryParams) {
        var inputs = queryParams.entrySet().stream()
                .map(entry -> "<input type=\"hidden\" name=\"" + entry.getKey() + "\" value=\"" + entry.getValue() + "\" />")
                .collect(Collectors.joining("\n"));
        return """
            <html>
               <head><title>Submit This Form</title></head>
               <body onload="javascript:document.forms[0].submit()">
                <form method="post" action="%s">
                  %s
                </form>
               </body>
            </html>
            """.formatted(target, inputs);
    }

    private static @NotNull Map<String, String> gatherInputFields(String response) {
        Matcher m2 = INPUT_PATTERN.matcher(response);
        Map<String, String> parameters = new HashMap<>();
        while (m2.find()) {
            parameters.put(unescapeXml(m2.group(1)), unescapeXml(m2.group(2)));
        }
        return parameters;
    }

    private static String extractTargetUrl(String body) {
        Matcher matcher = FORM_PATTERN.matcher(body);
        if (!matcher.find()) throw new RuntimeException("Invalid form post response");
        return matcher.group(1);
    }

}
