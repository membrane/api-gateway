/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.config.security.SSLParser;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.util.text.SerializationUtil.Serialization.URL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetTest {

    @Test
    void defaultEscaping() {
        assertEquals(URL,new Target().getEscaping());
    }

    @Test
    void defaultPortWithoutSsl() {
        assertEquals(80, new Target().getPort());
    }

    @Test
    void defaultPortWithSsl() {
        Target target = new Target();
        target.setSslParser(new SSLParser());
        assertEquals(443, target.getPort());
    }

    @Test
    void explicitPortIsKept() {
        Target target = new Target();
        target.setSslParser(new SSLParser());
        target.setPort(8080);
        assertEquals(8080, target.getPort());
    }
}