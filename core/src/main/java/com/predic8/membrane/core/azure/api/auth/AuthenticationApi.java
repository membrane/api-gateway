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
package com.predic8.membrane.core.azure.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.azure.AzureIdentity;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.openapi.serviceproxy.ApiDocsInterceptor;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthenticationApi {
    private final HttpClient http;
    private final AzureIdentity config;
    private final Map<String, String> tokenPayload;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationApi.class);

    public AuthenticationApi(HttpClient http, @Nullable AzureIdentity config) {
        this.http = http;
        this.config = config;

        if (config == null) {
            tokenPayload = Map.of();
            return;
        }

        tokenPayload = Map.of(
                "grant_type", config.getGrantType(),
                "client_id", config.getClientId(),
                "client_secret", config.getClientSecret(),
                "resource", config.getResource()
        );
    }

    public String accessToken() throws Exception {
        Exchange exc = tokenExchange();
        var responseBody = http.call(exc).getResponse().getBodyAsStringDecoded();
        try {
            return new ObjectMapper()
                    .readTree(responseBody)
                    .get("access_token")
                    .asText();
        } catch (Exception e) {
            log.debug(e.getMessage());
            log.debug(exc.getRequest().toString());
            log.debug(exc.getResponse().getHeader().toString());
            log.debug(responseBody);
        }
        return "";
    }

    private Exchange tokenExchange() throws URISyntaxException {
        var tenantId = config.getTenantId();
        return new Request.Builder()
                .post("https://login.microsoftonline.com/" + tenantId + "/oauth2/token")
                .body(tokenPayload.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&")))
                .buildExchange();
    }
}
