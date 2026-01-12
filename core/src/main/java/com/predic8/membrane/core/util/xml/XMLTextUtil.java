/* Copyright 2009, 2026 predic8 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.util.xml;


import com.predic8.xml.beautifier.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;


public class XMLTextUtil {

    private static final Logger log = LoggerFactory.getLogger(XMLTextUtil.class.getName());

    // Guess for a very short XML
    private static final int STRING_BUFFER_INITIAL_CAPACITY_FOR_XML = 250;

    public static String formatXML(Reader reader) throws Exception {
        return formatXML(reader, false);
    }

    /**
     * As HTML is needed for the AdminConsole.
     *
     * @param reader XML input
     * @param asHTML if {@code true}, format for HTML display (e.g. in the AdminConsole);
     *               otherwise format as plain XML.
     * @return formatted string
     * @throws Exception if parsing or formatting fails
     */
    public static String formatXML(Reader reader, boolean asHTML) throws Exception {
        try {
            StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(getXmlBeautifierFormatter(asHTML, out)).parse(reader);
            return out.toString();
        } catch (IOException e) {
            log.info("Error parsing XML: {}", e.getMessage());
            throw e;
        }
    }

    public static String formatXML(InputStream inputStream, boolean asHTML) throws Exception {
        try {
            StringWriter out = new StringWriter(STRING_BUFFER_INITIAL_CAPACITY_FOR_XML);
            new XMLBeautifier(getXmlBeautifierFormatter(asHTML, out)).parse(inputStream);
            return out.toString();
        } catch (IOException e) {
            log.info("Error parsing XML: {}", e.getMessage());
            throw e;
        }
    }

    private static @NotNull XMLBeautifierFormatter getXmlBeautifierFormatter(boolean asHTML, StringWriter out) {
        return asHTML ? new HtmlBeautifierFormatter(out, 0) : new StandardXMLBeautifierFormatter(out, 4);
    }

    /**
     * Checks whether s is a valid (well-formed and balanced) XML snippet.
     */
    public static boolean isValidXMLSnippet(String s) {
        try {
            XMLEventReader parser = XMLInputFactoryFactory.inputFactory()
                    .createXMLEventReader(new StringReader("<a>" + s + "</a>"));
            XMLEvent event = null;
            try {
                while (parser.hasNext()) {
                    event = parser.nextEvent();
                }
                return event != null && event.isEndDocument();
            } finally {
                try {
                    parser.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            log.debug("Invalid XML snippet.", e);
            return false;
        }
    }
}
