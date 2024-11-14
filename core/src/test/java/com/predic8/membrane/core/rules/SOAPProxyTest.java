package com.predic8.membrane.core.rules;

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.transport.http.*;
import org.junit.jupiter.api.*;

public class SOAPProxyTest {

    Router router;

    SOAPProxy proxy;

    @BeforeEach
    void setUp() throws Exception {

        proxy = new SOAPProxy();
        router = new Router();
        router.setTransport(new HttpTransport());
        router.setExchangeStore(new ForgetfulExchangeStore());
        router.add(proxy);
    }

    @Test
    void parseWSDL() throws Exception {
        proxy.setWsdl("classpath:/ws/cities.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultiplePortsPerService() throws Exception {
        proxy.setWsdl("classpath:/blz-service.wsdl");
        router.init();
    }

    @Test
    void parseWSDLWithMultipleServices() {
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        Assertions.assertThrows(IllegalArgumentException.class, () -> router.init());
    }

    @Test
    void parseWSDLWithMultipleServicesForAGivenService() throws Exception {
        proxy.setServiceName("CityServiceA");
        proxy.setWsdl("classpath:/ws/cities-2-services.wsdl");
        router.init();
    }


}