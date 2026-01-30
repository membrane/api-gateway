/* Copyright 2023 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.router.Router;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.predic8.membrane.core.interceptor.authentication.SecurityUtils.*;

/**
 * @description A <i>user data provider</i> utilizing <code>htpasswd</code>-style files.
 * <p>
 *   The <i>fileUserDataProvider</i> loads users from a file in the format
 *   <code>username:hash</code> (one entry per line).
 * </p>
 * <p>
 *   Supported hash formats are <i>crypt(3)</i>-style hashes
 *   (<code>$&lt;id&gt;$&lt;salt&gt;$&lt;hash&gt;</code>, optionally including <code>rounds=&lt;n&gt;</code>)
 *   and bcrypt hashes (<code>$2a$</code>, <code>$2b$</code>, <code>$2y$</code>).
 *   The Apache <code>$apr1$...</code> format is not supported.
 * </p>
 */
@MCElement(name="fileUserDataProvider")
public class FileUserDataProvider implements UserDataProvider {
    private final Map<String, User> usersByName = new HashMap<>();

    private String htpasswdPath;

    /**
     * @description A path pointing to the htpasswd file.
     */
    @MCAttribute
    public void setHtpasswdPath(String path) {
        this.htpasswdPath = path;
    }

    public String getHtpasswdPath() { return this.htpasswdPath; }

    @Override
    public Map<String, String> verify(Map<String, String> postData) {
        String username = postData.get("username");
        if (username == null) throw new NoSuchElementException();

        User userAttributes = getUsersByName().get(username);
        if (userAttributes == null) throw new NoSuchElementException();

        verifyLoginOrThrow(postData, userAttributes.getPassword());
        return userAttributes.getAttributes();
    }

    public static class User {
        final Map<String, String> attributes = new HashMap<>();

        public User(String username, String password){
            setUsername(username);
            setPassword(password);
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

    public Map<String, User> getUsersByName() {
        return usersByName;
    }

    @Override
    public void init(Router router) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(this.htpasswdPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String line : lines) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                User user = new User(parts[0], parts[1]);
                getUsersByName().put(user.getUsername(), user);
            }
        }
    }
}
