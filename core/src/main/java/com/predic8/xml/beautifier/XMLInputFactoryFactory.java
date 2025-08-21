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

package com.predic8.xml.beautifier;

import javax.xml.stream.*;
import java.io.*;

import static javax.xml.stream.XMLInputFactory.*;

public final class XMLInputFactoryFactory {

    public static final String JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES = "javax.xml.stream.isSupportingExternalEntities";

    private static final ThreadLocal<XMLInputFactory> TL = ThreadLocal.withInitial(() -> {


        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(IS_COALESCING, Boolean.FALSE); // CDATA stays CDATA
        f.setProperty(SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(IS_NAMESPACE_AWARE, Boolean.TRUE);

        // Do not replace internal character references to avoid XML bombs that deflate the message size
        f.setProperty(IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);

        // Defensive hardening: disable external entities if supported and use a no-op resolver.
        try {
            f.setProperty(JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        } catch (IllegalArgumentException ignore) {
            // property not supported by this implementation
        }

        // Disable entity resolving
        f.setXMLResolver((publicId, systemId, baseURI, namespace) -> new ByteArrayInputStream(new byte[0]));

        return f;
    });

    private XMLInputFactoryFactory() {
    }

    public static XMLInputFactory inputFactory() {
        return TL.get();
    }
}

