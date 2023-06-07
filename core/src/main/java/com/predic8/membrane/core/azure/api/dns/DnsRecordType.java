package com.predic8.membrane.core.azure.api.dns;

public enum DnsRecordType {
    TXT("txt")
    ;

    private final String value;

    DnsRecordType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
