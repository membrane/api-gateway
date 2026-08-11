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

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.List;

/**
 * @description The checks applied to the inbound <code>wsse:Security</code> header, in the order
 * they are listed. The whole list runs before any <code>secure</code> part does, and the header is
 * consumed - removed from the message - at that boundary.
 */
@MCElement(name = "validate", component = false, noEnvelope = true, id = "wsSecurity-validate")
public class ValidateGroup {

    private List<ValidatePart> parts = List.of();

    public List<ValidatePart> getValidateParts() {
        return parts;
    }

    @MCChildElement
    public void setValidateParts(List<ValidatePart> parts) {
        this.parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
