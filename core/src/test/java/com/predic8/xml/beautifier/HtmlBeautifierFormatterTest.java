/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.xml.beautifier;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlBeautifierFormatterTest {

    @Test
    void textIsEscaped() throws Exception {
        final var html = format("<a>&lt;img src=x onerror=alert(1)&gt;</a>");
        assertFalse(html.contains("<img"), html);
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"), html);
    }

    @Test
    void attributeValueIsEscaped() throws Exception {
        final var html = format("<a b=\"&lt;img src=x onerror=alert(1)&gt;\"/>");
        assertFalse(html.contains("<img"), html);
    }

    @Test
    void linkedAttributeValueCannotBreakOutOfHref() throws Exception {
        final var html = format("<a b=\"http://x&quot; onmouseover=&quot;alert(1)\"/>");
        assertFalse(html.contains("\" onmouseover=\""), html);
        assertTrue(html.contains("http://x&quot; onmouseover=&quot;alert(1)"), html);
    }

    @Test
    void namespaceUriIsEscaped() throws Exception {
        final var html = format("<a xmlns=\"&lt;img src=x onerror=alert(1)&gt;\" xmlns:p=\"&lt;svg onload=alert(1)&gt;\"/>");
        assertFalse(html.contains("<img"), html);
        assertFalse(html.contains("<svg"), html);
    }

    @Test
    void ampersandInTextStaysVisible() throws Exception {
        assertTrue(format("<a>&amp;lt;</a>").contains("&amp;lt;"));
    }

    private static String format(String xml) throws Exception {
        final var out = new StringWriter();
        new XMLBeautifier(new HtmlBeautifierFormatter(out, 0)).parse(new StringReader(xml));
        return out.toString();
    }
}
