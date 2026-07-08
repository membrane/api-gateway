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

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;

/**
 * Factory for XXE-hardened {@link SchemaFactory} and {@link Validator} instances.
 * {@link SchemaFactory} is not thread-safe and cannot be shared, so each call to
 * {@link #newInstance} returns a fresh instance.
 * Callers may still set an {@link org.w3c.dom.ls.LSResourceResolver} after construction —
 * that resolver handles legitimate external schema imports via xs:import and is unaffected
 * by the DTD hardening.
 */
public final class HardenedSchemaFactory {

    private HardenedSchemaFactory() {}

    /**
     * Returns a new {@link SchemaFactory} for the given schema language with external DTD
     * access blocked. Note: ACCESS_EXTERNAL_SCHEMA is intentionally left at its default so
     * that legitimate xs:import resolution through an LSResourceResolver continues to work.
     *
     * @param schemaLanguage e.g. {@code XMLConstants.W3C_XML_SCHEMA_NS_URI}
     */
    public static SchemaFactory newInstance(String schemaLanguage) {
        SchemaFactory sf = SchemaFactory.newInstance(schemaLanguage);
        try {
            sf.setProperty(ACCESS_EXTERNAL_DTD, "");
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
