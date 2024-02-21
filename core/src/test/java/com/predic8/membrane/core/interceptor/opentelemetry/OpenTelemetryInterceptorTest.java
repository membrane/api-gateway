package com.predic8.membrane.core.interceptor.opentelemetry;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.RuleManager;
import com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtelExporter;
import com.predic8.membrane.core.interceptor.opentelemetry.exporter.OtlpExporter;
import com.predic8.membrane.core.interceptor.soap.SampleSoapServiceInterceptor;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.ServiceProxy;
import com.predic8.membrane.core.rules.ServiceProxyKey;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.predic8.membrane.core.RuleManager.RuleDefinitionSource.SPRING;
import static org.junit.jupiter.api.Assertions.*;

class OpenTelemetryInterceptorTest {

    @Test
    void initTest() throws Exception {
        Rule r = new ServiceProxy(new ServiceProxyKey("localhost", "POST", ".*", 3141), null, 0);
        r.getInterceptors().add(new OpenTelemetryInterceptor());

        HttpRouter rtr = new HttpRouter();
        rtr.getRuleManager().addProxy(r, SPRING);

        rtr.init();
    }
}