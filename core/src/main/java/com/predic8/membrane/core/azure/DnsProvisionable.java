package com.predic8.membrane.core.azure;

public interface DnsProvisionable {
    void provisionDns(String domain, String record);
}
