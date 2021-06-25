package com.predic8.membrane.integration;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.WSDLInterceptor;
import com.predic8.membrane.core.interceptor.server.WSDLPublisherInterceptor;
import com.predic8.membrane.core.rules.AbstractServiceProxy;
import com.predic8.membrane.core.rules.InternalProxy;
import com.predic8.membrane.core.rules.Rule;
import com.predic8.membrane.core.rules.SOAPProxy;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SoapAndInternalProxyTest {

    @Test
    public void test() throws Exception {
        HttpRouter router = new HttpRouter();
        router.setHotDeploy(false);
        router.setRules(Lists.newArrayList(createSoapProxy(), createInternalProxy()));
        router.start();

        HttpClient hc = new HttpClient();
        Response r1 = hc.call(new Request.Builder().get("http://localhost:3047/b?wsdl").buildExchange()).getResponse();
        r1.write(System.out, true);
        assertTrue(r1.getBodyAsStringDecoded().contains("<soap12:address location=\"https://a.b.local/b\">"));

        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:wsaw=\"http://www.w3.org/2006/05/addressing/wsdl\" xmlns:http=\"http://schemas.xmlsoap.org/wsdl/http/\" xmlns:tns=\"http://thomas-bayer.com/blz/\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:mime=\"http://schemas.xmlsoap.org/wsdl/mime/\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" xmlns:soap12=\"http://schemas.xmlsoap.org/wsdl/soap12/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ><SOAP-ENV:Body><tns:getBank xmlns:tns=\"http://thomas-bayer.com/blz/\"><tns:blz>38060186</tns:blz></tns:getBank></SOAP-ENV:Body></SOAP-ENV:Envelope> ";

        Response r2 = hc.call(new Request.Builder().post("http://localhost:3047/b").body(body).buildExchange()).getResponse();
        r2.write(System.out,true);
        assertTrue(r2.getBodyAsStringDecoded().contains("GENODED1BRS"));

        router.shutdown();
    }

    private Rule createInternalProxy() {
        InternalProxy internalProxy = new InternalProxy();
        internalProxy.setName("int");
        AbstractServiceProxy.Target target = new AbstractServiceProxy.Target();
        target.setHost("www.thomas-bayer.com");
        target.setPort(80);
        internalProxy.setTarget(target);
        return internalProxy;
    }

    private Rule createSoapProxy() {
        SOAPProxy soapProxy = new SOAPProxy();
        soapProxy.setPort(3047);
        soapProxy.setWsdl("service:int/axis2/services/BLZService?wsdl");
        Path path = new Path();
        path.setValue("/b");
        soapProxy.setPath(path);

        WSDLInterceptor e = new WSDLInterceptor();
        e.setPort("443");
        e.setProtocol("https");
        e.setHost("a.b.local");
        soapProxy.getInterceptors().add(e);

        soapProxy.getInterceptors().add(new WSDLPublisherInterceptor());

        return soapProxy;
    }
}
