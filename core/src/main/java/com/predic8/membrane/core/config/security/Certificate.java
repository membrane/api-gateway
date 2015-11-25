/* Copyright 2015 predic8 GmbH, www.predic8.com

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

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.annot.MCTextContent;

@MCElement(name="certificate", mixed=true)
public class Certificate {

    String content;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Certificate))
            return false;
        Certificate other = (Certificate)obj;
        return Objects.equal(content, other.content);
    }

    public String getContent() {
        return content;
    }

    /**
     * @description The certificate in PEM format.
     */
    @MCTextContent
    public void setContent(String content) {
        this.content = content;
    }
}
