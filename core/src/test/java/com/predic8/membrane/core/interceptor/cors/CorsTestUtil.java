/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.cors;

import com.predic8.membrane.core.http.*;

import java.util.*;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;

public class CorsTestUtil {

    private static final String ACCESS_CONTROL_ALLOW_PREFIX = "access-control-allow";

    public static Set<String> getAccessControlAllowHeaderNames(Header header) {
        return stream(header.getAllHeaderFields())
                .map(hf -> hf.getHeaderName().getName().toLowerCase())
                .filter(n -> n.startsWith(ACCESS_CONTROL_ALLOW_PREFIX))
                .collect(toSet());
    }
}
