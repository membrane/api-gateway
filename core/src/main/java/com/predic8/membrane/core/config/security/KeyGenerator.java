/* Copyright 2016 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.config.security;

import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

/**
 * @description Experimental.
 * <p>Allows to dynamically generate keys/certificates for arbitrary domain names on the fly, signed by a specified
 * root CA key.</p>
 * <p>This is an alternative for {@link Key} and {@link KeyStore}.</p>
 */
@MCElement(name="keyGenerator")
public class KeyGenerator {

    private Key key;

    public Key getKey() {
        return key;
    }

    @MCChildElement
    public void setKey(Key key) {
        this.key = key;
    }
}
