/* Copyright 2009, 2021 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.soap;

import com.google.common.collect.*;
import com.predic8.membrane.core.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.server.*;
import com.predic8.membrane.core.proxies.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;

import static io.restassured.RestAssured.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;
import static org.hamcrest.Matchers.*;

/**
 * Checks the combination of a soapProxy and internalProxy, using "service:internalProxyName/path/to/the?wsdl".
 * <p>
 */
public class SoapAndInternalProxyTest {

    HttpRouter router;

    @BeforeEach
    void setup() {
        router = new HttpRouter();
        router.setHotDeploy(false);
    }

    @AfterEach
    void teardown() {
        router.shutdown();
    }

    @Test
    void test() throws Exception {
        router.setRules(Lists.newArrayList(createInternalProxy()));
        router.start();
        Proxy soapProxy = createServiceProxyWithWSDLInterceptors();
        soapProxy.init(router);
        router.add(soapProxy);
        runCheck();
    }

    private void runCheck() throws Exception {

        // @formatter:off
        given()
            .get("http://localhost:3047/b?wsdl")
        .then()
            .body("definitions.service.port.address.@location", equalTo("https://a.b.local/"));

        given()
            .body(getBody())
            .post("http://localhost:3047")
        .then()
            .body("Envelope.Body.getCityResponse.country", equalTo("Germany"));
        // @formatter:on
    }

    private @NotNull String getBody() throws IOException {
        return new String(requireNonNull(this.getClass().getResourceAsStream("/get-city.xml")).readAllBytes(), UTF_8);
    }

    private Proxy createInternalProxy() {
        InternalProxy internalProxy = new InternalProxy();
        internalProxy.setName("int");
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        internalProxy.getInterceptors().add(new SampleSoapServiceInterceptor());
        target.setHost("localhost");
        target.setPort(9501);

        internalProxy.setTarget(target);
        return internalProxy;
    }

    private Proxy createServiceProxyWithWSDLInterceptors() {
        ServiceProxy sp = new ServiceProxy();
        sp.setPort(3047);
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setUrl("internal://int");
        sp.setTarget(target);

        WSDLInterceptor e = new WSDLInterceptor();
        e.setPort("443");
        e.setProtocol("https");
        e.setHost("a.b.local");
        sp.getInterceptors().add(e);

        WSDLPublisherInterceptor publisher = new WSDLPublisherInterceptor();
        publisher.setWsdl("service://int/?wsdl");
        sp.getInterceptors().add(publisher);
        return sp;
    }
}