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

package com.predic8.membrane.core.util;

import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.charset.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class HttpTestUtil {

    /**
     * Converts a String with an HTTP message into an InputStream
     * @param request String with HTTP Message like GET / or HTTP/1.1 200 Ok
     * @return InputStream with the HTTP Message
     */
    public static @NotNull InputStream convertMessage(String request) {
        return new ByteArrayInputStream(request.stripIndent().replace("\n", "\r\n").getBytes(UTF_8));
    }
}
