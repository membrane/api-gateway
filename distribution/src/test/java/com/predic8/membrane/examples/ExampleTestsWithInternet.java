package com.predic8.membrane.examples;

import com.predic8.membrane.examples.config.GettingStartedTest;
import com.predic8.membrane.examples.config.ProxiesXMLSoapTest;
import com.predic8.membrane.examples.config.ProxiesXMLTest;
import com.predic8.membrane.examples.env.AntInPath;
import com.predic8.membrane.examples.env.HelpLinkExistenceTest;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing4XmlSessionTest;
import com.predic8.membrane.examples.tests.openapi.APIProxyTest;
import com.predic8.membrane.examples.tests.ssl.SSLServerApiWithTlsTest;
import com.predic8.membrane.examples.tests.ssl.ToBackendTest;
import com.predic8.membrane.examples.tests.validation.FormValidationTest;
import com.predic8.membrane.examples.tests.versioning.RoutingTest;
import com.predic8.membrane.examples.tests.versioning.XsltExampleTest;
import com.predic8.membrane.examples.tutorials.rest.TutorialRestInitialTest;
import com.predic8.membrane.examples.tutorials.rest.TutorialRestStepsTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        GettingStartedTest.class,  // See: https://membrane-api.io/getting-started

        AntInPath.class,
        ExampleTestsWithoutInternet.class,

        HelpLinkExistenceTest.class,

        ACLTest.class,
        BasicAuthTest.class,
        FileExchangeStoreTest.class,
        Loadbalancing4XmlSessionTest.class,
        LoggingCSVTest.class,
        LoggingJDBCTest.class,
        LoggingTest.class,
        LoginTest.class,
//        QuickstartRESTTest.class,
//        QuickstartSOAPTest.class,
        REST2SOAPTest.class,
        REST2SOAPJSONTest.class,
        RewriterTest.class,
        SSLServerApiWithTlsTest.class,
        ToBackendTest.class,
        ThrottleTest.class,
        XSLTTest.class,

        FormValidationTest.class,

        CustomInterceptorTest.class,
        StaxExampleInterceptorTest.class,
        AddSoapHeaderTest.class,
        BasicXmlInterceptorTest.class,

        RoutingTest.class,
        XsltExampleTest.class,

        // OpenAPI
        APIProxyTest.class,

        // Tutorials
        TutorialRestStepsTest.class,
        TutorialRestInitialTest.class,

        // Configuration
        ProxiesXMLTest.class,
        ProxiesXMLSoapTest.class,
})
public class ExampleTestsWithInternet {
}
