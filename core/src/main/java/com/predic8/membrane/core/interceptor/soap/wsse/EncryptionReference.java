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
 * @description One element to encrypt, listed in a <code>secure</code>/<code>encrypt</code>'s
 * <code>references</code>, or one that must have arrived encrypted, listed in a
 * <code>validate</code>/<code>decrypt</code>'s <code>requiredReferences</code>. Selects the target
 * either by a well-known name (<code>BODY</code>, <code>USERNAME_TOKEN</code>) or by an XPath
 * expression (set <code>xpath</code>; <code>by</code> is then inferred and must be omitted).
 * <p><code>type</code> decides whether the element itself or only its content is replaced by the
 * <code>xenc:EncryptedData</code>. The default is derived from the target: the
 * <code>soap:Body</code> is encrypted by <code>CONTENT</code>, because replacing the body element
 * itself would leave an envelope that is no longer valid SOAP, while anything else is encrypted as
 * a whole <code>ELEMENT</code>, so that its name does not stay in the clear.</p>
 */
@MCElement(name = "reference", component = false, id = "wsSecurity-encrypt-reference")
public class EncryptionReference {

    /**
     * Deliberately not {@link SignatureReference.By}. That enum has to carry values which are
     * meaningless or harmful here - <code>ENCRYPTED_KEY</code> (encrypting the key that decrypts the
     * message), <code>HEADER</code> (encrypting the <code>wsse:Security</code> header that carries
     * that key), <code>BST</code> (a token that only ever holds a signing certificate) and
     * <code>TIMESTAMP</code> (which a receiver must be able to read to enforce freshness). Sharing
     * one enum would put all four on the configuration surface only to reject them.
     */
    public enum By {BODY, USERNAME_TOKEN, XPATH}

    public enum Type {CONTENT, ELEMENT}

    private By by = By.BODY;
    private boolean byExplicitlySet;
    private Type type;
    private String xpath;
    private String id;

    public By getBy() {
        return xpath != null ? By.XPATH : by;
    }

    /**
     * @description Which element to encrypt. <code>BODY</code> selects the SOAP body,
     * <code>USERNAME_TOKEN</code> the <code>wsse:UsernameToken</code> inside
     * <code>wsse:Security</code>. Must be omitted when {@link #setXpath(String)} is set — in that
     * case the reference is always resolved by XPath.
     * @default BODY
     */
    @MCAttribute
    public void setBy(By by) {
        this.by = by;
        this.byExplicitlySet = true;
    }

    public Type getType() {
        return type != null ? type : (getBy() == By.BODY ? Type.CONTENT : Type.ELEMENT);
    }

    /**
     * @description Whether the <code>xenc:EncryptedData</code> replaces the referenced element's
     * children (<code>CONTENT</code>) or the element itself (<code>ELEMENT</code>). When omitted,
     * <code>BODY</code> uses <code>CONTENT</code> and every other target uses <code>ELEMENT</code>.
     * <code>ELEMENT</code> is not allowed on <code>BODY</code>.
     * @example ELEMENT
     */
    @MCAttribute
    public void setType(Type type) {
        this.type = type;
    }

    public String getXpath() {
        return xpath;
    }

    /**
     * @description XPath expression selecting the element(s) to encrypt. Must match at least one
     * element; when it matches more than one, each matched element is encrypted individually.
     * Setting <code>xpath</code> implies {@link #setBy(By)} is <code>XPATH</code>; <code>by</code>
     * must then be omitted. The <code>soap</code>, <code>wsse</code>, and <code>wsu</code> prefixes
     * are always available; additional prefixes can be declared on the enclosing
     * <code>wsSecurity</code> element's <code>xmlConfig</code>.
     * @example //*[local-name()='creditCard']
     */
    @MCAttribute
    public void setXpath(String xpath) {
        this.xpath = xpath;
    }

    public String getId() {
        return id;
    }

    /**
     * @description The <code>Id</code> to assign to the <code>xenc:EncryptedData</code> this
     * reference produces, which the <code>xenc:ReferenceList</code> then points at. If omitted, one
     * is generated. Only applies when encrypting; it is unused in
     * <code>requiredReferences</code>.
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
        if (getBy() == By.BODY && type == Type.ELEMENT) {
            throw new ConfigurationException(
                    "reference: type: ELEMENT is not allowed with by: BODY — replacing soap:Body with an " +
                    "xenc:EncryptedData would not be a valid SOAP envelope. Use type: CONTENT (the default).");
        }
    }
}
