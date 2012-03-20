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

package com.predic8.membrane.core;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

/**
 * 
 * Fixes Issue with StAX RI parser, that doesn't correctly lookup attribute values with getAttributeValue when one uses null or "" as namespace parameter. 
 * That Issue is fixed since update 18.
 */
public class FixedStreamReader extends StreamReaderDelegate {
    public FixedStreamReader(XMLStreamReader rdr) {
        super(rdr);
    }

    @Override
    public String getAttributeValue(String ns, String lName) {
        if (!"".equals(ns) && ns != null) {
            return super.getAttributeValue(ns, lName);
        }

        for (int i = 0; i < getAttributeCount(); i++) {
            if ( (ns == null || getAttributeNamespace(i) == null || "".equals(getAttributeNamespace(i))) && lName.equals(getAttributeLocalName(i))) {
                return getAttributeValue(i);
            }
        }
        return null;
    }
} 