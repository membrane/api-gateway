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

import com.predic8.membrane.examples.config.GettingStartedTest;
import com.predic8.membrane.examples.config.ProxiesXMLFullSampleTest;
import com.predic8.membrane.examples.config.ProxiesXMLSoapExampleTest;
import com.predic8.membrane.examples.config.ProxiesXMLTest;
import com.predic8.membrane.examples.env.HelpLinkExistenceTest;
import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.tests.loadbalancing.Loadbalancing4XmlSessionTest;
import com.predic8.membrane.examples.tests.openapi.APIProxyExampleTest;
import com.predic8.membrane.examples.tests.ssl.SSLServerApiWithTlsPemTest;
import com.predic8.membrane.examples.tests.ssl.SSLServerApiWithTlsPkcs12Test;
import com.predic8.membrane.examples.tests.ssl.ToBackendTest;
import com.predic8.membrane.examples.tests.validation.FormValidationTest;
import com.predic8.membrane.examples.tests.versioning.RoutingTest;
import com.predic8.membrane.examples.tests.versioning.XsltExampleTest;
import com.predic8.membrane.examples.tutorials.rest.TutorialRestInitialTest;
import com.predic8.membrane.examples.tutorials.rest.TutorialRestStepsTest;
import org.junit.platform.suite.api.*;

@Suite
@SelectClasses({
        GettingStartedTest.class,  // See: https://membrane-api.io/getting-started
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
        SSLServerApiWithTlsPkcs12Test.class,
        SSLServerApiWithTlsPemTest.class,
        ToBackendTest.class,
        ThrottleTest.class,
        XSLTTest.class,

        FormValidationTest.class,
        CustomInterceptorTest.class,
        BasicXmlInterceptorTest.class,

        RoutingTest.class,
        XsltExampleTest.class,

        // OpenAPI
        APIProxyExampleTest.class,

        // Tutorials
        TutorialRestStepsTest.class,
        TutorialRestInitialTest.class,

        // Configuration
        ProxiesXMLTest.class,
        ProxiesXMLSoapExampleTest.class,
        ProxiesXMLFullSampleTest.class

})
@ExcludeClassNamePatterns("com.predic8.membrane.examples.env.HelpLinkExistenceTest")
class ExampleTestsWithInternet {
}