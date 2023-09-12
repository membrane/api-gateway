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
