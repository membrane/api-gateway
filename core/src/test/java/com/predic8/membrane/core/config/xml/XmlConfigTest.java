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

package com.predic8.membrane.core.config.xml;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.lang.ExchangeExpression.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.config.xml.XmlConfig.*;
import static org.junit.jupiter.api.Assertions.*;

class XmlConfigTest {

    DefaultRouter router;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void noGlobal() {
        assertNull(global(router));
        assertNull(localOrGlobal(null, router));
    }

    @Test
    void noRouter() {
        assertNull(global(null));
        final var local = new XmlConfig();
        assertSame(local, localOrGlobal(local, null));
    }

    @Test
    void globalIsUsedWithoutLocal() {
        final var global = new XmlConfig();
        router.getRegistry().register("ns", global);
        assertSame(global, global(router));
        assertSame(global, localOrGlobal(null, router));
    }

    @Test
    void localReplacesGlobal() {
        router.getRegistry().register("ns", new XmlConfig());
        final var local = new XmlConfig();
        assertSame(local, localOrGlobal(local, router));
    }

    @Test
    void interceptor() {
        final var global = new XmlConfig();
        router.getRegistry().register("ns", global);
        final var local = new XmlConfig();
        assertNull(localOrGlobal((Interceptor) null));
        assertSame(global, localOrGlobal(new InterceptorAdapter(router)));
        assertSame(local, localOrGlobal(new InterceptorAdapter(router, local)));
    }

    @Test
    void interceptorWithoutXmlSupport() {
        final var global = new XmlConfig();
        router.getRegistry().register("ns", global);
        assertSame(global, localOrGlobal(new AbstractInterceptor() {{
            this.router = XmlConfigTest.this.router;
        }}));
    }
}
