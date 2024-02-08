/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.oauth2client;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;

@MCElement(name = "loginParameter")
public class LoginParameter {

    private String name;
    private String value;

    public String getName() {
        return name;
    }

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @MCAttribute
    public void setValue(String value) {
        this.value = value;
    }
}
