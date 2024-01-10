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
package com.predic8.membrane.core.interceptor.acl.matchers.Cidr;

import com.predic8.membrane.core.interceptor.acl.TypeMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class CidrMatcher implements TypeMatcher {
    @Override
    public boolean matches(String value, String schema) {
        try {
            IpRange cr = IpRange.fromCidr(schema);
            return cr.contains(value);
        } catch (UnknownHostException | IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }
}
