/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.soap;

import com.predic8.membrane.core.util.*;
import org.junit.jupiter.api.*;

import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.util.soap.SoapVersion.*;
import static org.junit.jupiter.api.Assertions.*;

class SoapVersionTest {

    @Test
    void parseCorrect() {
        assertEquals(SOAP_11, parse("11"));
        assertEquals(SOAP_11, parse("1.1"));
        assertEquals(SOAP_12, parse("12"));
        assertEquals(SOAP_12, parse("1.2"));
    }

    @Test
    void parseInvalid() {
        assertThrows(ConfigurationException.class, () -> parse("invalid"));
    }

    @Test
    void getString() {
        assertEquals("1.1", SOAP_11.toString());
        assertEquals("1.2", SOAP_12.toString());
    }

    @Test
    void getContentType_mappingIsCorrect() {
        assertEquals(TEXT_XML, SOAP_11.getContentType());
        assertEquals(APPLICATION_SOAP, SOAP_12.getContentType());
    }
}

