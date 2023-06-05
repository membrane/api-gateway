package com.predic8.membrane.core.azure.api.records;

public enum DnsRecordType {
    TXT("txt")
    ;

    private String value;

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
