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

package com.predic8.membrane.core.interceptor.xmlprotection;

import org.jetbrains.annotations.NotNull;

import javax.xml.stream.events.DTD;
import javax.xml.stream.events.EntityDeclaration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Answers whether a {@code DOCTYPE} points outside the document it appeared in - by declaring an
 * external entity, or by referring to an external subset. Nothing here resolves such a reference;
 * it only reports that one is there, so that {@link XMLProtector} can decide what to do about it.
 *
 * <p>Both answers are read off the {@link DTD} event alone, one of them from the declaration text,
 * as StAX exposes no parsed form of the external identifier.</p>
 */
final class DoctypeInspector {

    // Word-bounded so DOCTYPE names that merely contain the keywords (e.g. PUBLICATIONS) don't match
    private static final Pattern EXTERNAL_ID_KEYWORD = Pattern.compile("\\b(?:SYSTEM|PUBLIC)\\b");

    private DoctypeInspector() {
    }

    @SuppressWarnings("unchecked") // DTD.getEntities() is a raw List of EntityDeclaration by contract
    static boolean containsExternalEntityReferences(DTD dtd) {
        List<EntityDeclaration> entities = dtd.getEntities();
        return entities != null && entities.stream()
                .anyMatch(entity -> entity.getPublicId() != null || entity.getSystemId() != null);
    }

    static boolean hasExternalSubsetReference(DTD dtd) {
        var decl = dtd.getDocumentTypeDeclaration();
        if (decl == null) return false;
        // Only inspect the header before the internal subset '[' - SYSTEM/PUBLIC only appear there as keywords
        return EXTERNAL_ID_KEYWORD.matcher(getHeaderAfterRootName(getHeader(decl))).find();
    }

    private static @NotNull String getHeader(String decl) {
        int internalSubset = decl.indexOf('[');
        return internalSubset >= 0 ? decl.substring(0, internalSubset) : decl;
    }

    /**
     * Per the {@link DTD#getDocumentTypeDeclaration()} contract, the header always starts with
     * "DOCTYPE" followed by the declared root element name (e.g. "&lt;!DOCTYPE SYSTEM ..."). That
     * name can legally be "SYSTEM" or "PUBLIC" itself, so it must be skipped before keyword-matching
     * for an actual external identifier — otherwise such a root name would be mistaken for one.
     */
    static @NotNull String getHeaderAfterRootName(String header) {
        int doctypeIdx = header.indexOf("DOCTYPE");
        int i = doctypeIdx >= 0 ? doctypeIdx + "DOCTYPE".length() : 0;
        while (i < header.length() && Character.isWhitespace(header.charAt(i))) i++;
        while (i < header.length() && !Character.isWhitespace(header.charAt(i))) i++;
        return header.substring(i);
    }
}
