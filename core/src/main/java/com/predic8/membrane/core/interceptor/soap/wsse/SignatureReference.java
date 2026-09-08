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
import com.predic8.membrane.core.util.ConfigurationException;

/**
 * @description One element to sign or verify, referenced from a <code>secure</code>/<code>signature</code>'s
 * <code>references</code> list, or a <code>validate</code>/<code>signature</code>'s
 * <code>requiredReferences</code> list. Selects the target either by a well-known
 * name (<code>BODY</code>, <code>HEADER</code>, <code>TIMESTAMP</code>, <code>BST</code>) or by an
 * XPath expression (set <code>xpath</code>; <code>by</code> is then inferred and must be omitted).
 * {@link #setId(String)} only applies when signing; it's unused when verifying.
 */
@MCElement(name = "reference", component = false, id = "wsSecurity-signature-reference")
public class SignatureReference {

    public enum By {BODY, HEADER, TIMESTAMP, USERNAME_TOKEN, XPATH, BST}

    private By by = By.BODY;
    private boolean byExplicitlySet;
    private String xpath;
    private String id;

    public By getBy() {
        return xpath != null ? By.XPATH : by;
    }

    /**
     * @description Which element to sign or verify. <code>BODY</code>/<code>HEADER</code> select
     * the SOAP body/header. <code>TIMESTAMP</code> selects an existing <code>wsu:Timestamp</code>
     * inside <code>wsse:Security</code>. <code>USERNAME_TOKEN</code> selects the
     * <code>wsse:UsernameToken</code> there — required for the common signed-UsernameToken policy,
     * where the signature is what binds the credential to this message instead of leaving it
     * replayable on its own. <code>BST</code> selects the
     * <code>wsse:BinarySecurityToken</code> created for the <code>securityTokenReference</code>
     * KeyInfo mode, so it is itself covered by the signature; only valid when
     * <code>securityTokenReference</code> is configured on the enclosing
     * <code>signature</code>. Must be omitted when {@link #setXpath(String)} is
     * set — in that case the reference is always resolved by XPath.
     * @default BODY
     */
    @MCAttribute
    public void setBy(By by) {
        this.by = by;
        this.byExplicitlySet = true;
    }

    public String getXpath() {
        return xpath;
    }

    /**
     * @description XPath expression selecting the element(s) to sign or verify. Must match at
     * least one element; when it matches more than one, each matched element is signed/verified
     * individually. Setting <code>xpath</code> implies {@link #setBy(By)} is <code>XPATH</code>;
     * <code>by</code> must then be omitted. The <code>soap</code>, <code>wsse</code>, and
     * <code>wsu</code> prefixes are always available; additional prefixes can be declared on the
     * enclosing <code>wsSecurity</code> element's <code>xmlConfig</code>.
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
     * one is generated. Not allowed when an <code>XPATH</code> reference matches more than one
     * element, since a single id can't apply to all of them.
     */
    @MCAttribute
    public void setId(String id) {
        this.id = id;
    }

    void validate() {
        if (xpath != null && byExplicitlySet) {
            throw new ConfigurationException(
                    "reference: 'by' must be omitted when 'xpath' is set — it is inferred as XPATH.");
        }
        if (xpath == null && by == By.XPATH) {
            throw new ConfigurationException("reference: by: XPATH requires an 'xpath' attribute.");
        }
    }
}
