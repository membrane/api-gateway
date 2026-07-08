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

import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import static javax.xml.XMLConstants.*;

/**
 * Factory for XXE-hardened {@link SchemaFactory} and {@link Validator} instances.
 * {@link SchemaFactory} is not thread-safe and cannot be shared, so each call to
 * {@link #newInstance} returns a fresh instance.
 * <p>
 * Explicitly enabling {@code FEATURE_SECURE_PROCESSING} resets both {@code ACCESS_EXTERNAL_DTD}
 * and {@code ACCESS_EXTERNAL_SCHEMA} to the empty string, so the factory itself never fetches
 * external resources. Legitimate xs:import/xs:include resolution still works because callers
 * install an {@link org.w3c.dom.ls.LSResourceResolver} (see ResolverMap#toLSResourceResolver),
 * which is consulted before the factory's own — now blocked — fetching.
 */
public final class HardenedSchemaFactory {

    private HardenedSchemaFactory() {}

    /**
     * Returns a new {@link SchemaFactory} for the given schema language with all parser-side
     * external DTD and schema fetching blocked. External schema references (xs:import,
     * xs:include) must be served by an LSResourceResolver set by the caller.
     *
     * @param schemaLanguage e.g. {@code XMLConstants.W3C_XML_SCHEMA_NS_URI}
     */
    public static SchemaFactory newInstance(String schemaLanguage) {
        var sf = SchemaFactory.newInstance(schemaLanguage);
        try {
            sf.setFeature(FEATURE_SECURE_PROCESSING, true);
            // Redundant after FEATURE_SECURE_PROCESSING (which already resets both to "") —
            // kept as an explicit guard against JAXP implementations that behave differently.
            sf.setProperty(ACCESS_EXTERNAL_DTD, "");
            sf.setProperty(ACCESS_EXTERNAL_SCHEMA, "");
        } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
            throw new IllegalStateException("Secure SchemaFactory properties not supported", e);
        }
        return sf;
    }

    /**
     * Hardens a {@link Validator} against DOCTYPE-based SSRF: blocks external DTD fetching
     * when the validator parses an instance document.
     */
    public static void hardenValidator(Validator v) {
        try {
            v.setProperty(ACCESS_EXTERNAL_DTD, "");
        } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
            throw new IllegalStateException("Secure Validator property not supported", e);
        }
    }
}
