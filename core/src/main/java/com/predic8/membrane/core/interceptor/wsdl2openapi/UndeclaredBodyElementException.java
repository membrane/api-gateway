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
 * The SOAP Body carries an element that no message of the WSDL port declares, so there is no
 * schema to convert it with. Thrown by {@link SoapBodySchemas#forElement}.
 */
class UndeclaredBodyElementException extends Exception {

    private final QName element;

    UndeclaredBodyElementException(QName element, String portName) {
        super("The element %s is not declared by any message of port '%s'.".formatted(element, portName));
        this.element = element;
    }

    /** The element the Body carries. */
    QName getElement() {
        return element;
    }
}
