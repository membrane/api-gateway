package com.predic8.membrane.core.azure.api.dns;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DnsRecordCommandExecutor {

    private final DnsRecordApi api;
    private final String basePath;
    private int ttl = 3600;

    private final List<SupportedDnsRecordType> records = new ArrayList<>();

    public DnsRecordCommandExecutor(DnsRecordApi api, String recordSetName, DnsRecordType type) {
        this.api = api;

        var resourceBasePath = String.format("%s/subscriptions/%s/resourceGroups/%s/providers",
                api.config().getResource(),
                api.config().getSubscriptionId(),
                api.config().getResourceGroup()
                );

        basePath = String.format("%s/Microsoft.Network/dnsZones/%s/%s/%s?api-version=2018-05-01",
                resourceBasePath,
                api.config().getDnsZoneName(),
                type.toString(),
                recordSetName
                );
    }

    public DnsRecordCommandExecutor ttl(int ttl) {
        this.ttl = ttl;
        return this;
    }

    public JsonNode create() throws Exception {
        Map<String, Object> properties = new HashMap<>(Map.of("TTL", ttl));

        records.forEach(record -> properties.putAll(record.payload()));

        var payload = Map.of("properties", properties);

        var response = api.http().call(
                api.requestBuilder()
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
        api.http().call(
            api.requestBuilder()
                    .delete(basePath)
                    .header("Accept", "application/json")
                    .buildExchange()
        );
    }
}
