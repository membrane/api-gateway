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

package com.predic8.membrane.core.interceptor.authentication.session;

import java.util.*;

public class User {

    final Map<String, String> attributes = new HashMap<>();

    public User(String username, String password) {
        setUsername(username);
        setPassword(password);
    }

    public User() {
    }

    public String getUsername() {
        return attributes.get("username");
    }

    public void setUsername(String value) {
        attributes.put("username", value);
    }

    public String getPassword() {
        return attributes.get("password");
    }

    public void setPassword(String value) {
        attributes.put("password", value);
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes.putAll(attributes);
    }
}
