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
import com.predic8.membrane.examples.tests.rest2soap.*;
import com.predic8.membrane.examples.tests.ssl.*;
import com.predic8.membrane.examples.tests.validation.*;
import com.predic8.membrane.examples.tests.versioning.*;
import com.predic8.membrane.examples.tutorials.rest.*;
import org.junit.platform.suite.api.*;

@Suite
@SelectClasses({
        GettingStartedExampleTest.class,  // See: https://membrane-api.io/getting-started
        HelpLinkExistenceTest.class,

        ACLExampleTest.class,
        BasicAuthExampleTest.class,
        FileExchangeStoreExampleTest.class,
        Loadbalancing4XmlSessionExampleTest.class,
        LoggingCSVExampleTest.class,
        LoggingJDBCExampleTest.class,
        LoggingExampleTest.class,
        LoginExampleTest.class,
        RewriterExampleTest.class,
        SSLServerApiWithTlsPkcs12ExampleTest.class,
        SSLServerApiWithTlsPemExampleTest.class,
        ToBackendExampleTest.class,
        ThrottleExampleTest.class,
        XSLTExampleTest.class,

        FormValidationExampleTest.class,
        CustomInterceptorExampleTest.class,
        BasicXmlInterceptorExampleTest.class,

        XsltExampleTest.class,

        // OpenAPI
        APIProxyExampleTest.class,

        // Tutorials
        TutorialRestStepsExampleTest.class,
        TutorialRestInitialExampleTest.class,

        // Configuration
        ProxiesXMLExampleTest.class,
        ProxiesXMLSoapExampleTest.class,
        ProxiesXMLFullExampleTest.class,

        Rest2SOAPTemplateExampleTest.class

})
@ExcludeClassNamePatterns("com.predic8.membrane.examples.env.HelpLinkExistenceTest")
class ExampleTestsWithInternet {
}