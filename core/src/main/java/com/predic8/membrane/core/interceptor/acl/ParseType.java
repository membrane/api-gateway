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
package com.predic8.membrane.core.interceptor.acl;

import com.predic8.membrane.core.interceptor.acl.matchers.*;
import com.predic8.membrane.core.interceptor.acl.matchers.Cidr.CidrMatcher;

public enum ParseType {
    GLOB("glob", new GlobMatcher()),
    REGEX("regex", new RegexMatcher()),
    CIDR("cidr", new CidrMatcher());

    private final String value;
    private final TypeMatcher matcher;

    ParseType(String value, TypeMatcher matcher) {
        this.value = value;
        this.matcher = matcher;
    }

    public static ParseType getByOrDefault(String string) {
        if (string != null) {
            for (ParseType type : values()) {
                if (string.equalsIgnoreCase(type.value)) {
                    return type;
                }
            }
        }
        return GLOB;
    }

    @Override
    public String toString() {
        return value;
    }

    public TypeMatcher getMatcher() {
        return matcher;
    }
}
