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

package com.predic8.membrane.core.util.xml.parser;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Strategy interface for XML parsing.
 * Implementations are expected to be thread-safe and XXE-hardened.
 */
public interface XmlParser {

    /**
     * Parses the given XML input source into a DOM document.
     *
     * @param source the XML input source to parse
     * @return a DOM {@link Document}
     * @throws XmlParseException if parsing fails
     */
    Document parse(InputSource source) throws XmlParseException;
}
