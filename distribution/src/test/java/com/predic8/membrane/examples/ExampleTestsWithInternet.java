/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples;

import com.predic8.membrane.examples.config.*;
import com.predic8.membrane.examples.env.*;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.*;
import com.predic8.membrane.examples.tests.openapi.*;
import com.predic8.membrane.examples.tests.ssl.*;
import com.predic8.membrane.examples.tests.validation.*;
import com.predic8.membrane.examples.tests.versioning.*;
import com.predic8.membrane.examples.tutorials.rest.*;
import org.junit.platform.suite.api.*;

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

        // @Todo Replace with new SOAP demo service
        //ProxiesXMLSoapTest.class,
})
public class ExampleTestsWithInternet {
}
