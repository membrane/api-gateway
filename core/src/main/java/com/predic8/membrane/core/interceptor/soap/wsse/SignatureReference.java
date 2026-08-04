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
package com.predic8.membrane.core.interceptor.soap.wsse;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

/**
 * @description One element to sign or verify, referenced from a <code>digitalSignature</code>
 * interceptor's <code>references</code> list, or a <code>digitalSignatureVerifier</code>
 * interceptor's <code>requiredReferences</code> list. Selects the target either by a well-known
 * name (<code>BODY</code>, <code>HEADER</code>, <code>TIMESTAMP</code>) or by an XPath expression.
 * {@link #setId(String)} only applies when signing; it's unused when verifying.
 */
@MCElement(name = "reference", component = false, id = "digitalSignature-reference")
public class SignatureReference {

    public enum By {BODY, HEADER, TIMESTAMP, XPATH}

    private By by = By.BODY;
    private String xpath;
    private String id;

    public By getBy() {
        return by;
    }

    /**
     * @description Which element to sign or verify. <code>BODY</code>/<code>HEADER</code> select
     * the SOAP body/header. <code>TIMESTAMP</code> selects an existing <code>wsu:Timestamp</code>
     * inside <code>wsse:Security</code>. <code>XPATH</code> selects the element matched by
     * {@link #setXpath(String)}.
     * @default BODY
     */
    @MCAttribute
    public void setBy(By by) {
        this.by = by;
    }

    public String getXpath() {
        return xpath;
    }

    /**
     * @description XPath expression selecting the element. Must match exactly one element. Only
     * used when {@link #setBy(By)} is <code>XPATH</code>.
     * @example //*[local-name()='Timestamp']
     */
    @MCAttribute
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public String getId() {
        return id;
    }

    /**
     * @description The <code>wsu:Id</code> to assign to the referenced element. If omitted, an
     * existing <code>wsu:Id</code>/<code>Id</code> attribute on the element is reused, otherwise
     * one is generated.
     */
    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }
}
