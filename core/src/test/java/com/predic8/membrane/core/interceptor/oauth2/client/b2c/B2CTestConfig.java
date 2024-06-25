/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
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
