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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import java.io.ByteArrayInputStream;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static javax.xml.stream.XMLInputFactory.*;

/**
 * Thread-safe, XXE-hardened source of {@link XMLInputFactory} instances for StAX parsing.
 * Each thread gets its own cached factory, so hardening the {@code SUPPORT_DTD} /
 * {@code isSupportingExternalEntities} / resolver settings happens once per thread rather than
 * once per factory instance (factory creation takes ~10s for 1,000,000 instances; ThreadLocal
 * reuse takes ~0s).
 * <p>
 * {@link #inputFactory()} rejects DTDs outright. Callers that have to see the {@code DOCTYPE} event
 * to act on it themselves - {@link com.predic8.membrane.core.interceptor.xmlprotection.XMLProtector}
 * removes or rejects it - use {@link #dtdAwareInputFactory()} instead. Neither can trigger an
 * outbound network or file-system access: external entities and external subset fetching are
 * disabled via the resolver in both.
 */
public final class HardenedStaxInputFactory {

    public static final String JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES = "javax.xml.stream.isSupportingExternalEntities";

    private static final String JDK_XML_MAX_ELEMENT_DEPTH = "jdk.xml.maxElementDepth";
    private static final String JDK_XML_MAX_XML_NAME_LIMIT = "jdk.xml.maxXMLNameLimit";

    /** Both JAXP limits read 0 as "no limit", not as "nothing allowed". */
    private static final int NO_LIMIT = 0;

    /** Resolves every external reference to an empty document instead of fetching it. */
    private static final XMLResolver NO_EXTERNAL_ENTITIES =
            (publicId, systemId, baseURI, namespace) -> new ByteArrayInputStream(new byte[0]);

    private static final ThreadLocal<XMLInputFactory> TL = ThreadLocal.withInitial(() -> {
        XMLInputFactory f = harden(XMLInputFactory.newInstance());
        f.setProperty(SUPPORT_DTD, FALSE);
        return f;
    });

    private HardenedStaxInputFactory() {
    }

    public static XMLInputFactory inputFactory() {
        return TL.get();
    }

    /**
     * Returns a hardened factory that still reports {@code DOCTYPE} events and imposes neither a
     * nesting depth nor an XML name length of its own, for callers that inspect the DTD and cap
     * size themselves - as {@link com.predic8.membrane.core.interceptor.xmlprotection.XMLProtector}
     * does, and has to.
     * <p>
     * JAXP caps depth at 100 and name length at 1000 by default, and reports an exceeded cap as a
     * parse error at the same point a caller's own equal cap would fire. Left in place, the JDK
     * limit would both override a caller configured for larger documents and hide the real reason
     * behind "not well-formed", so both caps are handed to the caller instead. Note that the name
     * cap also covered attribute names and namespace URIs, which a caller limiting element names
     * does not replace; the message body itself stays bounded by the {@code limit} plugin.
     * <p>
     * Unlike {@link #inputFactory()} this creates a fresh instance on every call. Callers that parse
     * repeatedly should hold on to the returned factory - per thread, as factories are not shared
     * across threads here - rather than asking for a new one per message.
     */
    public static XMLInputFactory dtdAwareInputFactory() {
        XMLInputFactory f = harden(XMLInputFactory.newInstance());
        // Support DTDs on purpose, so the caller can detect them in its StAX loop
        f.setProperty(SUPPORT_DTD, TRUE);
        setIfSupported(f, JDK_XML_MAX_ELEMENT_DEPTH, NO_LIMIT);
        setIfSupported(f, JDK_XML_MAX_XML_NAME_LIMIT, NO_LIMIT);
        return f;
    }

    /**
     * Applies everything the two factories share; the caller decides on {@code SUPPORT_DTD}.
     */
    private static XMLInputFactory harden(XMLInputFactory f) {
        f.setProperty(IS_COALESCING, FALSE); // CDATA stays CDATA
        f.setProperty(IS_NAMESPACE_AWARE, TRUE);

        // Do not replace internal character references to avoid XML bombs that deflate the message size
        f.setProperty(IS_REPLACING_ENTITY_REFERENCES, FALSE);

        // Defensive hardening: disable external entities if supported and use a no-op resolver.
        setIfSupported(f, JAVAX_XML_STREAM_IS_SUPPORTING_EXTERNAL_ENTITIES, FALSE);
        f.setXMLResolver(NO_EXTERNAL_ENTITIES);

        // Ensure validation is disabled across implementations
        setIfSupported(f, IS_VALIDATING, FALSE);
        return f;
    }

    /**
     * Sets a property that not every StAX implementation knows, leaving the factory as it is when it
     * does not.
     */
    private static void setIfSupported(XMLInputFactory f, String property, Object value) {
        try {
            f.setProperty(property, value);
        } catch (IllegalArgumentException ignore) {
            // property not supported by this implementation
        }
    }
}

