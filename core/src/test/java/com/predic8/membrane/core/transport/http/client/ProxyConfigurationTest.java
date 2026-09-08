/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.client;

import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProxyConfigurationTest {

    ProxyConfiguration proxy;

    @BeforeEach
    void setUp() {
        proxy = new ProxyConfiguration();
        proxy.setHost("proxy.example.com");
        proxy.setPort(3128);
    }

    @Test
    void hostIsRequired() {
        proxy.setHost(" ");
        assertThrows(ConfigurationException.class, () -> proxy.validate());
    }

    @Test
    void authenticationWithoutCredentialsIsRejected() {
        proxy.setAuthentication(true);
        assertThrows(ConfigurationException.class, () -> proxy.validate());

        proxy.setUsername("alice");
        assertThrows(ConfigurationException.class, () -> proxy.validate());
    }

    @Test
    void credentialsAreNotNeededWithoutAuthentication() {
        assertDoesNotThrow(() -> proxy.validate());
    }

    @Test
    void authenticationWithCredentialsBuildsHeader() {
        proxy.setAuthentication(true);
        proxy.setUsername("alice");
        proxy.setPassword("secret");

        assertDoesNotThrow(() -> proxy.validate());
        assertEquals("Basic YWxpY2U6c2VjcmV0", proxy.getCredentials());
    }
}
