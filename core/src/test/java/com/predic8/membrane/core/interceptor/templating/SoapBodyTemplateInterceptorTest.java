package com.predic8.membrane.core.interceptor.templating;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SoapBodyTemplateInterceptorTest {

    @Test
    void setSOAPVersion() {
        setSoapVersion("1.1");
        setSoapVersion("1.2");
    }

    @Test
    void setWrongSOAPVersion() {
        assertThrows(ConfigurationException.class, () -> setSoapVersion("1.3"));
    }

    private static void setSoapVersion(String version) {
        SoapBodyTemplateInterceptor i = new SoapBodyTemplateInterceptor();
        i.setVersion(version);
        assertEquals(version, i.getVersion());
    }

}