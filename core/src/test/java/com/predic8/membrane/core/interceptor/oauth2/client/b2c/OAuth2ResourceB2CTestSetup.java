/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2.client.b2c;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.interceptor.oauth2.client.BrowserMock;
import com.predic8.membrane.core.interceptor.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class OAuth2ResourceB2CTestSetup {
    protected final Logger LOG = LoggerFactory.getLogger(OAuth2ResourceB2CTestSetup.class);
    protected final B2CTestConfig tc = new B2CTestConfig();
    protected final ObjectMapper om = new ObjectMapper();
    protected final AtomicBoolean didLogIn = new AtomicBoolean();
    protected final AtomicBoolean didLogOut = new AtomicBoolean();
    protected final BrowserMock browser = new BrowserMock();
    protected final MockAuthorizationServer mockAuthorizationServer = new MockAuthorizationServer(tc, () -> didLogIn.set(true), () -> didLogOut.set(true));
    protected final B2CMembrane b2cMembrane = new B2CMembrane(tc, createSessionManager());

    @BeforeEach
    void init() throws Exception {
        didLogIn.set(false);
        didLogOut.set(false);
        mockAuthorizationServer.resetBehavior();
        mockAuthorizationServer.init();
        b2cMembrane.init();
    }

    @AfterEach
    void done() {
        mockAuthorizationServer.stop();
        b2cMembrane.stop();
    }

    protected abstract SessionManager createSessionManager();
}
