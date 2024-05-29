package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import java.util.UUID;

public class B2CTestConfig {
    public final UUID tenantId = UUID.randomUUID();
    public final String userFullName = "Mem Brane";
    public final String idp = "https://demo.predic8.de/api";
    public final String sub = UUID.randomUUID().toString();
    public final String clientId = UUID.randomUUID().toString();
    public final String clientSecret = "3423233123123";
    public final String susiFlowId = "b2c_1_susi";
    public final String peFlowId = "b2c_1_profile_editing";
    public final String pe2FlowId = "b2c_1_profile_editing2";
    public final String api1Id = UUID.randomUUID().toString();
    public final String api2Id = UUID.randomUUID().toString();
    public final int limit = 500;
    public final int clientPort = 31337;

    public String getClientAddress() {
        return "http://localhost:" + clientPort;
    }
}
