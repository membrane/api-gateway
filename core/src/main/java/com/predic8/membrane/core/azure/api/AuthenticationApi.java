package com.predic8.membrane.core.azure.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.azure.AzureConfig;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpClient;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthenticationApi {

    private final HttpClient httpClient;
    private final AzureConfig azureConfig;
    private final Map<String, String> tokenPayload;

    public AuthenticationApi(HttpClient httpClient, AzureConfig azureConfig) {
        this.httpClient = httpClient;
        this.azureConfig = azureConfig;

        tokenPayload = Map.of(
                "grant_type", azureConfig.getGrantType(),
                "client_id", azureConfig.getClientId(),
                "client_secret", azureConfig.getClientSecret(),
                "resource", azureConfig.getResource()
        );
    }

    public String accessToken() throws Exception {
        var response = httpClient.call(tokenExchange()).getResponse();
        return new ObjectMapper()
                .readTree(response.getBodyAsStringDecoded())
                .get("access_token")
                .asText();
    }

    private Exchange tokenExchange() throws URISyntaxException {
        return new Request.Builder()
                .post("https://login.microsoftonline.com/" + azureConfig.getTenantId() + "/oauth2/token")
                .body(tokenPayload.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&")))
                .buildExchange();
    }
}
