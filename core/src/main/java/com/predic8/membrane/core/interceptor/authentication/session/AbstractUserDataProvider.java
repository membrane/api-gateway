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

import com.predic8.membrane.annot.*;

import java.util.*;

import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;

public abstract class AbstractUserDataProvider implements UserDataProvider {

    protected Map<String, User> usersByName = new HashMap<>();

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        var username = postData.get("username");
        if (username == null) throw new NoSuchElementException();

        var userAttributes = getUsersByName().get(username);
        if (userAttributes == null) throw new NoSuchElementException();

        verifyLoginOrThrow(postData, userAttributes.getPassword());
        return userAttributes.getAttributes();
    }

    public Map<String, User> getUsersByName() {
        return usersByName;
    }
}
