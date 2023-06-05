package com.predic8.membrane.core.azure.api.records;

import com.predic8.membrane.core.azure.api.DnsRecordCommandExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TxtRecordBuilder implements SupportedDnsRecordType {

    private DnsRecordCommandExecutor parent;

    public TxtRecordBuilder(DnsRecordCommandExecutor parent) {
        this.parent = parent;
    }

    private List<String> values = new ArrayList<>();

    public DnsRecordCommandExecutor withValue(String... values) {
        Collections.addAll(this.values, values);
        return parent;
    }

    @Override
    public Map<String, Map<String, List<String>>> payload() {
        return Map.of(
                "TXTRecords", Map.of(
                        "value", List.of(String.join("", values))
                )
        );
    }
}
