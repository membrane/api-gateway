package com.predic8.membrane.core.azure.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.azure.api.records.DnsRecordType;
import com.predic8.membrane.core.azure.api.records.SupportedDnsRecordType;
import com.predic8.membrane.core.azure.api.records.TxtRecordBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsRecordCommandExecutor {

    private final AzureApiClient apiClient;
    private String basePath;
    private int ttl = 3600;

    private List<SupportedDnsRecordType> records = new ArrayList<>();

    public DnsRecordCommandExecutor(AzureApiClient apiClient, String recordSetName, DnsRecordType type) {
        this.apiClient = apiClient;

        basePath = apiClient.config().apiResourceBasePath()
                + "/Microsoft.Network/dnsZones/"
                + apiClient.config().getDomainName()
                + "/" + type.toString()
                + "/" + recordSetName
                + "?api-version=2018-05-01";
    }

    public DnsRecordCommandExecutor ttl(int ttl) {
        this.ttl = ttl;
        return this;
    }

    public JsonNode create() throws Exception {
        Map<String, Object> properties = new HashMap<>(Map.of("TTL", ttl));

        records.forEach(record -> properties.putAll(record.payload()));

        var payload = Map.of("properties", properties);

        var response = apiClient.httpClient().call(
                apiClient.authenticatedRequestBuilder()
                        .put(basePath)
                        .contentType("application/json")
                        .body(new ObjectMapper().writeValueAsString(payload))
                        .buildExchange()
                )
                .getResponse();

        return new ObjectMapper().readTree(response.getBodyAsStringDecoded());
    }

    public TxtRecordBuilder addRecord() {
        var builder = new TxtRecordBuilder(this);
        records.add(builder);
        return builder;
    }

    public void delete() throws Exception {
        apiClient.httpClient().call(
            apiClient.authenticatedRequestBuilder()
                    .delete(basePath)
                    .header("Accept", "application/json")
                    .buildExchange()
        );
    }
}
