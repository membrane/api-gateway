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

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.router.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * @description A <i>user data provider</i> utilizing <code>htpasswd</code>-style files.
 * <p>
 *   The <i>htpasswdFileProvider</i> loads users from a file in the format
 *   <code>username:hash</code> (one entry per line).
 * </p>
 * <p>
 * Supported hash formats are <i>crypt(3)</i>-style hashes
 * (<code>$&lt;id&gt;$&lt;salt&gt;$&lt;hash&gt;</code>, optionally including <code>rounds=&lt;n&gt;</code>),
 * bcrypt hashes (<code>$2a$</code>, <code>$2b$</code>, <code>$2y$</code>) and argon2id hashes (<code>$argon2id$</code>)
 * with the strict format (<code>$argon2id$v=19$m=65536,t=3,p=1$...$...</code>, numbers may vary).
 * The Apache <code>$apr1$...</code> format is not supported.
 * </p>
 */
@MCElement(name="htpasswdFileProvider")
public class FileUserDataProvider extends AbstractUserDataProvider {
    private final Map<String, User> usersByName = new HashMap<>();

    private String location;

    /**
     * @description A path pointing to the htpasswd file.
     */
    @MCAttribute
    public void setLocation(String path) {
        this.location = path;
    }

    public String getLocation() { return this.location; }


    @Override
    public void init(Router router) {
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(this.location));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (String line : lines) {
            String[] parts = line.split(":", 2);  // FIX: limit to 2 parts
            if (parts.length == 2) {
                User user = new User(parts[0], parts[1]);
                getUsersByName().put(user.getUsername(), user);
            }
        }
    }
}
