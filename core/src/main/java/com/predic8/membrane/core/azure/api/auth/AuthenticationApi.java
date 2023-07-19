package com.predic8.membrane.core.azure.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.azure.AzureIdentity;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

import javax.annotation.Nullable;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthenticationApi {

    private final HttpClient http;
    private final AzureIdentity config;
    private final Map<String, String> tokenPayload;

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
        var response = http.call(tokenExchange()).getResponse();
        return new ObjectMapper()
                .readTree(response.getBodyAsStringDecoded())
                .get("access_token")
                .asText();
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
