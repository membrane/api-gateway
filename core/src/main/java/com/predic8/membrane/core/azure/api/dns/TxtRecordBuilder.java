package com.predic8.membrane.core.azure.api.dns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TxtRecordBuilder implements SupportedDnsRecordType {

    private final DnsRecordCommandExecutor parent;
    private final List<String> values = new ArrayList<>();

    public TxtRecordBuilder(DnsRecordCommandExecutor parent) {
        this.parent = parent;
    }

    public DnsRecordCommandExecutor withValue(String... values) {
        Collections.addAll(this.values, values);
        return parent;
    }

    @Override
    public Map<String, List<Map<String, List<String>>>> payload() {
        return Map.of(
                "TXTRecords", List.of(
                        Collections.singletonMap("value", List.of(String.join("", values)))
                )
        );
    }
}
