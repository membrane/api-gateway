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
package com.predic8.membrane.core.interceptor.wsdl2openapi;

import javax.xml.namespace.QName;

/**
 * A message carries an element that the WSDL or XSD does not declare, so there is no schema to
 * convert it with. Thrown by {@link SoapBodySchemas#forElement} for a SOAP Body, and by
 * {@link XmlSchemaMapping} for a document element or a JSON root key.
 */
public class UndeclaredElementException extends Exception {

    private final QName element;

    /**
     * @param declaredBy what was searched for a declaration, completing "is not declared by …",
     *                   e.g. {@code any message of port 'CityPort'}
     */
    UndeclaredElementException(QName element, String declaredBy) {
        super("The element %s is not declared by %s.".formatted(element, declaredBy));
        this.element = element;
    }

    /** The element the message carries. */
    public QName getElement() {
        return element;
    }
}
