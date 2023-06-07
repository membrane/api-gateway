package com.predic8.membrane.core.azure.api.dns;

public interface DnsProvisionable {
    void provisionDns(String domain, String record);
}
