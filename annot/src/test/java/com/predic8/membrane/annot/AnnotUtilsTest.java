/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.annot;

import org.junit.jupiter.api.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotUtilsTest {

    @Test
    void simple() throws Exception {
        assertEquals("<foo>123</foo>", str("""
                <e><foo>123</foo></e>
                """));
    }

    @Test
    void empty() throws Exception {
        assertEquals("<foo></foo>", str("""
                <e><foo/></e>
                """));
    }

    @Test
    void str() throws Exception {
        assertEquals("123", str("""
                <e>123</e>
                """));
    }

    @Test
    void nothing() throws Exception {
        assertEquals("", str("""
                <e></e>
                """));
    }

    @Test
    void attr() throws Exception {
        assertEquals("<foo bar=\"3\"></foo>", str("""
                <e><foo bar="3"/></e>
                """));
    }

    @Test
    void nested() throws Exception {
        assertEquals("<a><b><c></c></b></a>", str("""
                <e><a><b><c></c></b></a></e>
                """));
    }

    @Test
    void mixed() throws Exception {
        assertEquals("1<a>2<b>3<c>4</c>5</b>6</a>7", str("""
                <e>1<a>2<b>3<c>4</c>5</b>6</a>7</e>
                """));
    }

    @Test
    void ns() throws Exception {
        assertEquals("<foo ns=\"bar\"></foo>", str("""
                <e><foo ns="bar"></foo></e>
                """));
    }

    @Test
    void cdata() throws Exception {
        assertEquals("1<foo ns=\"bar\">234</foo>5", str("""
                <e>1<foo ns="bar">2<![CDATA[3]]>4</foo>5</e>
                """));
    }

    @Test
    void entities() throws Exception {
        assertEquals("1<foo>> < &</foo>2", str("""
                <e>1<foo>&gt; &lt; &amp;</foo>2</e>
                """));
    }


    private String str(String s) throws Exception {
        return AnnotUtils.getContentAsString(parse(s));
    }

    private Element parse(String xml) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(bais);
        return doc.getDocumentElement();
    }
}