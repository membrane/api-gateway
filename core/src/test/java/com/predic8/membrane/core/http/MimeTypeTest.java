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

package com.predic8.membrane.core.http;

import jakarta.mail.internet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static org.junit.jupiter.api.Assertions.*;

public class MimeTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"zip","octet-stream"})
    void isBinarySubtypes(String subtype) {
        assertTrue(MimeType.isBinary("foo/" + subtype),subtype);
    }

    @ParameterizedTest
    @ValueSource(strings = {"audio","image","video"})
    void isBinaryPrimaryTypes(String primary) {
        assertTrue(MimeType.isBinary(primary + "/foo"),primary);
    }

    @ParameterizedTest
    @ValueSource(strings = {"xml","xhtml","svg"})
    void isXML(String subtype) {
        assertTrue(MimeType.isXML(  "foo/" + subtype),subtype);
    }

    @Test
    void parameters() throws ParseException {
        ContentType ct = new ContentType("text/xml; charset=utf-8");
        ParameterList pl = ct.getParameterList();
        System.out.println("pl = " + pl.get("charset").toUpperCase());
    }
}