/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.examples.tests.openapi;

import com.predic8.membrane.examples.tests.*;
import com.predic8.membrane.examples.util.*;
import org.junit.jupiter.api.*;
import org.skyscreamer.jsonassert.*;

import java.io.*;

import static com.predic8.membrane.test.AssertUtils.*;

public class OpenAPIValidation extends DistributionExtractingTestcase {

    @Override
    protected String getExampleDirName() {
        return "openapi/validation";
    }

    private Process2 process;

    @BeforeEach
    void startMembrane() throws IOException, InterruptedException {
        process = startServiceProxyScript();
    }

    @AfterEach
    void stopMembrane() throws IOException, InterruptedException {
        process.killScript();
    }

    @Test
    public void validRequest() throws Exception {
        getAndAssert(200, URL_2000 + "/demo-api/v2/persons?limit=10");
    }

    @Test
    void OneOfWithRightInteger() throws Exception {
        putAndAssert(201, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                	"name": "Jan Vermeer",
                	"countryCode": "DE",
                	"address": {
                		"city": "Bonn",
                		"street": "Koblenzer Straße 65",
                		"zip": 53173
                	}
                }
                """);
    }

    @Test
    void OneOfWithWrongStringPattern() throws Exception {
        String res = putAndAssert(400, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                	"name": "Jan Vermeer",
                	"countryCode": "DE",
                	"address": {
                		"city": "Bonn",
                		"street": "Koblenzer Straße 65",
                		"zip": "D-5317"
                	}
                }
                """);
        JSONAssert.assertEquals("""
                {
                  "method" : "PUT",
                  "uriTemplate" : "/persons/{pid}",
                  "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                  "validationErrors" : {
                	"REQUEST/BODY#/address/zip" : [ {
                	  "message" : "OneOf requires that exactly one subschema is valid. But there are 0 subschemas valid.",
                	  "complexType" : "Address",
                	  "schemaType" : "object"
                	} ]
                  }
                }
                """, res, true);
    }

    @Test
    void nestedObject() throws Exception {
            putAndAssert(201, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                    {
                    	"name": "Jan Vermeer",
                    	"countryCode": "DE",
                    	"address": {
                    		"city": "Bonn",
                    		"street": "Koblenzer Straße 65",
                    		"zip": "D-53173"
                    	}
                    }
                    """);
    }

    @Test
    void wrongRegexPattern() throws Exception {
        String res = putAndAssert(400, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                	"name": "Jan Vermeer",
                	"countryCode": "Germany"
                }
                """);
        JSONAssert.assertEquals("""
                {
                   "method" : "PUT",
                   "uriTemplate" : "/persons/{pid}",
                   "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                   "validationErrors" : {
                     "REQUEST/BODY#/countryCode" : [ {
                       "message" : "The string 'Germany' is 7 characters long. MaxLength of 2 is exceeded.",
                       "complexType" : "Person",
                       "schemaType" : "string"
                     }, {
                       "message" : "The string 'Germany' does not match the regex pattern \\\\w{2}.",
                       "complexType" : "Person",
                       "schemaType" : "string"
                     } ]
                   }
                 }
                """, res, true);
    }

    @Test
    void additionalPropertyRole() throws IOException {
        String res = putAndAssert(400, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                {
                	"name": "Jan Vermeer",
                	"role": "admin"
                }
                """);
        JSONAssert.assertEquals("""
                {
                  "method" : "PUT",
                  "uriTemplate" : "/persons/{pid}",
                  "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                  "validationErrors" : {
                    "REQUEST/BODY" : [ {
                      "message" : "The object has the additional Property: role .But the schema does not allow additional properties.",
                      "complexType" : "Person",
                      "schemaType" : "object"
                    } ]
                  }
                }
                """, res, true);
    }

    @Test
    void RequiredPropertyIsMissing() throws IOException {
        String res = putAndAssert(400, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                  {
                      "email": "jan@predic8.de"
                  }
                """);
        JSONAssert.assertEquals("""
                {
                  "method" : "PUT",
                  "uriTemplate" : "/persons/{pid}",
                  "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1",
                  "validationErrors" : {
                    "REQUEST/BODY#/name" : [ {
                      "message" : "Required property name is missing.",
                      "complexType" : "Person",
                      "schemaType" : "object"
                    } ]
                  }
                }
                """, res, true);
    }

    @Test
    void wrongContentType() throws IOException {
        putAndAssert(415, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_XML_HEADER, """
                	<name>Jan</name>
                """);
    }

    @Test
    void invalidUUIDEmailAndEnum() throws IOException {
        String res = putAndAssert(400, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2+DDFC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                	{
                		"name": "Jan Vermeer",
                		"email": "jan(at)schilderei.nl",
                		"type": "ARTIST"
                	}
                """);
        JSONAssert.assertEquals("""
                {
                  "method" : "PUT",
                  "uriTemplate" : "/persons/{pid}",
                  "path" : "/demo-api/v2/persons/4077C19D-2C1D-427B-B2+DDFC3112CE89D1",
                  "validationErrors" : {
                	"REQUEST/BODY#/type" : [ {
                	  "message" : "The string 'ARTIST' does not contain a value from the enum PRIVAT,BUSINESS.",
                	  "complexType" : "Person",
                	  "schemaType" : "string"
                	} ],
                	"REQUEST/BODY#/email" : [ {
                	  "message" : "The string 'jan(at)schilderei.nl' is not a valid email.",
                	  "complexType" : "Person",
                	  "schemaType" : "string"
                	} ],
                	"REQUEST/PATH_PARAMETER/pid" : [ {
                	  "message" : "The string '4077C19D-2C1D-427B-B2+DDFC3112CE89D1' is not a valid UUID.",
                	  "schemaType" : "string"
                	} ]
                  }
                }
                """, res, true);
    }

    @Test
    void validPut() throws IOException {
        putAndAssert(201, URL_2000 + "/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1", CONTENT_TYPE_APP_JSON_HEADER, """
                	{
                		"name": "Jan Vermeer"
                	}
                """);
    }

    @Test
    void limitGreaterThan100() throws IOException {
        JSONAssert.assertEquals("""
                {
                	"method" : "GET",
                	"uriTemplate" : "/persons",
                	"path" : "/demo-api/v2/persons?limit=200",
                	"validationErrors" : {
                	  "REQUEST/QUERY_PARAMETER/limit" : [ {
                		"message" : "200.0 is greater than the maximum of 100",
                		"schemaType" : "integer"
                	  } ]
                	}
                  }
                """, getAndAssert(400, URL_2000 + "/demo-api/v2/persons?limit=200"), true);
    }

    @Test
    void wrongPath() throws IOException {
        JSONAssert.assertEquals("""
                {
                	"method" : "GET",
                	"path" : "/demo-api/v2/wrong",
                	"validationErrors" : {
                	  "REQUEST/PATH" : [ {
                		"message" : "Path /demo-api/v2/wrong is invalid."
                	  } ]
                	}
                 }
                """, getAndAssert(404, URL_2000 + "/demo-api/v2/wrong"), true);
    }
}
